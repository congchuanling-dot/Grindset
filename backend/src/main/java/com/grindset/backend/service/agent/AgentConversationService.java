package com.grindset.backend.service.agent;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grindset.backend.common.BizException;
import com.grindset.backend.common.ErrorCode;
import com.grindset.backend.common.SnowflakeIdGenerator;
import com.grindset.backend.common.Texts;
import com.grindset.backend.dto.AgentDtos;
import com.grindset.backend.entity.AgentConversation;
import com.grindset.backend.entity.AgentMessage;
import com.grindset.backend.llm.ChatMessage;
import com.grindset.backend.llm.LlmTarget;
import com.grindset.backend.llm.LlmToolCall;
import com.grindset.backend.mapper.AgentConversationMapper;
import com.grindset.backend.mapper.AgentMessageMapper;
import com.grindset.backend.service.ModelCatalogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;

import static com.grindset.backend.common.Texts.truncate;
import static com.grindset.backend.common.constant.InputLimits.CHAT_MAX_MESSAGE_LENGTH;
import static com.grindset.backend.common.constant.TimeFormats.STANDARD;

/**
 * 首页 Agent 会话的读写入口。
 *
 * <p>职责分三块:</p>
 * <ol>
 *   <li>会话维度 CRUD:列表、详情、重命名、删除,归属校验失败统一 404;</li>
 *   <li>一轮对话的落库:开始时写用户提问,结束时把工具调用和回答一次性写入;</li>
 *   <li>历史重放:把库里的消息还原成 LLM 能直接吃的消息链。</li>
 * </ol>
 *
 * <p>写库刻意分成"开始时一次 + 结束时批量一次",不在流式过程中逐字更新,
 * 否则一条回答几百个 delta 会变成几百次 UPDATE。</p>
 */
@Service
public class AgentConversationService {

    private static final Logger log = LoggerFactory.getLogger(AgentConversationService.class);

    /** 列表最多返回多少条会话 */
    private static final int LIST_LIMIT = 100;

    /** 重放历史时最多读多少条消息,防止极端会话把上下文和内存撑爆 */
    private static final int MAX_REPLAY_MESSAGES = 200;

    /** 标题生成最多等多久;等不到就用占位标题,不能卡住 done 事件 */
    private static final long TITLE_WAIT_SECONDS = 3;

    /** 占位标题长度,与 LLM 生成标题的上限保持一致 */
    private static final int TITLE_PREVIEW_LENGTH = 15;

    private static final int STATUS_OK = 0;
    private static final int STATUS_FAILED = 2;
    private static final int STATUS_INTERRUPTED = 3;

    private final AgentConversationMapper conversationMapper;
    private final AgentMessageMapper messageMapper;
    private final AgentService agentService;
    private final AgentTitleService titleService;
    private final ModelCatalogService catalog;
    private final SnowflakeIdGenerator idGenerator;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    /** 一个会话一把锁:同一会话的生成、重命名、删除必须互斥 */
    private final ConcurrentHashMap<Long, ReentrantLock> locks = new ConcurrentHashMap<>();

    public AgentConversationService(AgentConversationMapper conversationMapper,
                                    AgentMessageMapper messageMapper,
                                    AgentService agentService,
                                    AgentTitleService titleService,
                                    ModelCatalogService catalog,
                                    SnowflakeIdGenerator idGenerator,
                                    TransactionTemplate transactionTemplate,
                                    ObjectMapper objectMapper) {
        this.conversationMapper = conversationMapper;
        this.messageMapper = messageMapper;
        this.agentService = agentService;
        this.titleService = titleService;
        this.catalog = catalog;
        this.idGenerator = idGenerator;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
    }

    /** 同一会话共用一把锁,按需创建 */
    private ReentrantLock lockOf(long conversationId) {
        return locks.computeIfAbsent(conversationId, key -> new ReentrantLock());
    }

    // ------------------------------------------------------------------
    // 一轮对话
    // ------------------------------------------------------------------

    /**
     * 跑一轮 Agent 对话并落库。
     *
     * <p>有 conversationId 时历史从库里重放;没有则先建会话。
     * 用户提问先落库,回答在流结束后批量写入,客户端中断也会保留已生成的部分。</p>
     */
    public AgentDtos.ChatResult chat(long userId, AgentDtos.ChatRequest request, AgentStreamSink sink) {
        if (request == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请求不能为空");
        }
        String question = Texts.strip(request.getMessage());
        if (question.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "消息不能为空");
        }
        if (question.length() > CHAT_MAX_MESSAGE_LENGTH) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "单条消息不能超过 " + CHAT_MAX_MESSAGE_LENGTH + " 字");
        }

        // 先校验模型是否可用,避免把用户提问落库后才发现模型配错
        String model = Texts.strip(request.getModel());
        List<LlmTarget> targets = catalog.requireTargets(Texts.strip(request.getProvider()), model);

        AgentConversation conversation = resolveConversation(
                userId, Texts.strip(request.getConversationId()), question);

        ReentrantLock lock = lockOf(conversation.getId());
        if (!lock.tryLock()) {
            throw new BizException(ErrorCode.CONFLICT, "该会话正在生成回答，请稍候");
        }
        try {
            // 读会话和加锁之间可能被并发删除,锁内再确认一次,避免往已删除的会话里写消息
            conversation = conversationMapper.selectById(conversation.getId());
            if (conversation == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "会话不存在");
            }

            // 首轮才需要生成标题;后续轮次沿用现有标题
            boolean firstTurn = intValue(conversation.getMessageCount()) == 0;

            // 历史必须在写入本轮提问之前读,否则本次提问会重复
            List<ChatMessage> history = loadHistory(conversation.getId());
            history.add(new ChatMessage(ChatMessage.ROLE_USER, question));

            appendUserMessage(conversation, question);
            sink.conversation(String.valueOf(conversation.getId()), conversation.getTitle());

            // 标题和回答并行生成,回答结束时标题通常已经好了
            CompletableFuture<String> titleFuture = firstTurn
                    ? titleService.generateAsync(targets, model, question, conversation.getTitle())
                    : CompletableFuture.completedFuture(conversation.getTitle());

            RecordingSink recorder = new RecordingSink(sink);
            AgentService.Turn turn;
            try {
                turn = agentService.stream(userId, targets, model, history, recorder);
            } catch (RuntimeException e) {
                // 模型/业务失败:错误文案也落库,刷新后能看到失败原因;异常继续抛给 Controller 发 error 事件
                persistErrorMessage(conversation, errorMessage(e), titleFuture.getNow(conversation.getTitle()));
                throw e;
            }

            String title = awaitTitle(titleFuture, conversation.getTitle());
            persistTurn(conversation, turn, recorder, title);
            return new AgentDtos.ChatResult(String.valueOf(conversation.getId()), title);
        } finally {
            lock.unlock();
        }
    }

    // ------------------------------------------------------------------
    // 会话 CRUD
    // ------------------------------------------------------------------

    /** 会话列表,按最后消息时间倒序 */
    public List<AgentDtos.ConversationSummary> list(long userId) {
        List<AgentConversation> conversations = conversationMapper.selectList(
                new QueryWrapper<AgentConversation>()
                        .eq("user_id", userId)
                        .orderByDesc("last_message_at")
                        .orderByDesc("id")
                        .last("LIMIT " + LIST_LIMIT));
        List<AgentDtos.ConversationSummary> result = new ArrayList<>(conversations.size());
        for (AgentConversation conversation : conversations) {
            result.add(new AgentDtos.ConversationSummary(
                    String.valueOf(conversation.getId()),
                    conversation.getTitle(),
                    intValue(conversation.getMessageCount()),
                    format(conversation.getLastMessageAt()),
                    format(conversation.getCreatedAt())));
        }
        return result;
    }

    /** 会话详情,消息已按一轮工具调用合并成展示结构 */
    public AgentDtos.ConversationDetail detail(long userId, long id) {
        AgentConversation conversation = requireOwned(userId, id);
        List<AgentMessage> rows = messageMapper.selectList(new QueryWrapper<AgentMessage>()
                .eq("conversation_id", id)
                .orderByAsc("seq"));
        return new AgentDtos.ConversationDetail(
                String.valueOf(conversation.getId()),
                conversation.getTitle(),
                intValue(conversation.getMessageCount()),
                format(conversation.getLastMessageAt()),
                format(conversation.getCreatedAt()),
                toViews(rows));
    }

    /** 重命名会话。生成中不允许改,避免和本轮结束时的标题更新互相覆盖 */
    public void rename(long userId, long id, String title) {
        String value = Texts.strip(title);
        if (value.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "标题不能为空");
        }
        if (value.length() > 64) {
            throw new BizException(ErrorCode.BAD_REQUEST, "标题不能超过 64 个字符");
        }
        requireOwned(userId, id);

        ReentrantLock lock = lockOf(id);
        if (!lock.tryLock()) {
            throw new BizException(ErrorCode.CONFLICT, "该会话正在生成回答，请稍候");
        }
        try {
            AgentConversation patch = new AgentConversation();
            patch.setId(id);
            patch.setTitle(value);
            conversationMapper.updateById(patch);
        } finally {
            lock.unlock();
        }
    }

    /** 删除会话及其全部消息。生成中不允许删,否则流结束时会往已删除的会话里写消息 */
    public void delete(long userId, long id) {
        requireOwned(userId, id);

        ReentrantLock lock = lockOf(id);
        if (!lock.tryLock()) {
            throw new BizException(ErrorCode.CONFLICT, "该会话正在生成回答，请稍候");
        }
        try {
            transactionTemplate.executeWithoutResult(status -> {
                messageMapper.delete(new QueryWrapper<AgentMessage>().eq("conversation_id", id));
                conversationMapper.deleteById(id);
            });
        } finally {
            lock.unlock();
        }
    }

    /** 归属校验失败统一 404,避免泄露会话是否存在 */
    private AgentConversation requireOwned(long userId, long id) {
        AgentConversation conversation = conversationMapper.selectById(id);
        if (conversation == null || conversation.getUserId() == null || conversation.getUserId() != userId) {
            throw new BizException(ErrorCode.NOT_FOUND, "会话不存在");
        }
        return conversation;
    }

    // ------------------------------------------------------------------
    // 写入
    // ------------------------------------------------------------------

    /** 没有 conversationId 时懒创建一个新会话,标题先用问题截断占位 */
    private AgentConversation createConversation(long userId, String question) {
        LocalDateTime now = LocalDateTime.now();
        AgentConversation conversation = new AgentConversation();
        conversation.setId(idGenerator.nextId());
        conversation.setUserId(userId);
        conversation.setTitle(previewTitle(question));
        conversation.setMessageCount(0);
        // 时间统一由 Java 写入,避免和数据库 CURRENT_TIMESTAMP 的时区差异混用
        conversation.setLastMessageAt(now);
        conversation.setCreatedAt(now);
        conversationMapper.insert(conversation);
        return conversation;
    }

    private AgentConversation resolveConversation(long userId, String rawId, String question) {
        if (rawId.isEmpty()) {
            return createConversation(userId, question);
        }
        try {
            return requireOwned(userId, Long.parseLong(rawId));
        } catch (NumberFormatException e) {
            throw new BizException(ErrorCode.BAD_REQUEST, "会话 ID 不合法");
        }
    }

    /** 用户提问先落库,保证刷新页面后至少能看到自己问了什么 */
    private void appendUserMessage(AgentConversation conversation, String question) {
        int seq = intValue(conversation.getMessageCount()) + 1;
        LocalDateTime now = LocalDateTime.now();
        transactionTemplate.executeWithoutResult(status -> {
            AgentMessage row = new AgentMessage();
            row.setId(idGenerator.nextId());
            row.setConversationId(conversation.getId());
            row.setSeq(seq);
            row.setRole(ChatMessage.ROLE_USER.toUpperCase());
            row.setContent(question);
            row.setStatus(STATUS_OK);
            row.setCreatedAt(now);
            messageMapper.insert(row);

            conversation.setMessageCount(seq);
            conversation.setLastMessageAt(now);
            updateMeta(conversation.getId(), seq, now, null);
        });
    }

    /**
     * 只更新会话的元数据,不整行覆盖。
     * 整行 updateById 会把读出来的旧标题一起写回去,和用户重命名竞争时容易覆盖。
     */
    private void updateMeta(long conversationId, int messageCount,
                            LocalDateTime lastMessageAt, String title) {
        AgentConversation patch = new AgentConversation();
        patch.setId(conversationId);
        patch.setMessageCount(messageCount);
        patch.setLastMessageAt(lastMessageAt);
        patch.setTitle(title);
        conversationMapper.updateById(patch);
    }

    /**
     * 保证落库的 turn 是完整可重放的。
     *
     * <p>客户端在工具执行中途断开时,最后一条 assistant(tool_calls) 可能还没来得及补齐 tool 结果。
     * 这种半组留在库里,下次重放会被上游判为非法消息序列,所以直接丢掉;
     * 如果整轮没有正常结尾,再补一条空的中断回复,让前端能显示"生成已中断"。</p>
     */
    private static List<ChatMessage> normalizeTurn(AgentService.Turn turn) {
        List<ChatMessage> messages = new ArrayList<>(turn.messages());

        // 找到最后一条工具调用,检查它的每个 tool_call_id 是否都有结果
        int lastCallIndex = -1;
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            if (ChatMessage.ROLE_ASSISTANT.equals(message.role())
                    && message.toolCalls() != null && !message.toolCalls().isEmpty()) {
                lastCallIndex = i;
                break;
            }
        }
        if (lastCallIndex >= 0) {
            List<String> pending = new ArrayList<>();
            for (LlmToolCall call : messages.get(lastCallIndex).toolCalls()) {
                pending.add(call.id());
            }
            for (int i = lastCallIndex + 1; i < messages.size(); i++) {
                ChatMessage message = messages.get(i);
                if (ChatMessage.ROLE_TOOL.equals(message.role())) {
                    pending.remove(message.toolCallId());
                }
            }
            if (!pending.isEmpty()) {
                messages = new ArrayList<>(messages.subList(0, lastCallIndex));
            }
        }

        if (turn.aborted() && !endsWithAnswer(messages)) {
            messages.add(new ChatMessage(ChatMessage.ROLE_ASSISTANT, ""));
        }
        return messages;
    }

    private static boolean endsWithAnswer(List<ChatMessage> messages) {
        if (messages.isEmpty()) {
            return false;
        }
        ChatMessage last = messages.get(messages.size() - 1);
        return ChatMessage.ROLE_ASSISTANT.equals(last.role())
                && (last.toolCalls() == null || last.toolCalls().isEmpty());
    }

    /** 把本轮工具调用和最终回答按顺序批量写入 */
    private void persistTurn(AgentConversation conversation, AgentService.Turn turn,
                             RecordingSink recorder, String title) {
        List<ChatMessage> messages = normalizeTurn(turn);
        int startSeq = intValue(conversation.getMessageCount());
        LocalDateTime now = LocalDateTime.now();
        transactionTemplate.executeWithoutResult(status -> {
            int seq = startSeq;
            for (int i = 0; i < messages.size(); i++) {
                ChatMessage message = messages.get(i);
                boolean last = i == messages.size() - 1;

                AgentMessage row = new AgentMessage();
                row.setId(idGenerator.nextId());
                row.setConversationId(conversation.getId());
                row.setSeq(++seq);
                row.setRole(message.role().toUpperCase());
                row.setCreatedAt(now);

                if (message.toolCalls() != null && !message.toolCalls().isEmpty()) {
                    row.setToolCallsJson(writeJson(message.toolCalls()));
                } else {
                    row.setContent(message.content() == null ? "" : message.content());
                }
                if (ChatMessage.ROLE_TOOL.equals(message.role())) {
                    row.setToolCallId(message.toolCallId());
                    row.setToolMetaJson(writeJson(recorder.metaOf(message.toolCallId())));
                }
                // 只有最后一轮普通助手消息带失败/中断状态,工具调用行始终是正常的
                boolean finalAnswer = ChatMessage.ROLE_ASSISTANT.equals(message.role())
                        && (message.toolCalls() == null || message.toolCalls().isEmpty());
                row.setStatus(finalAnswer && last && turn.aborted() ? STATUS_INTERRUPTED : STATUS_OK);
                messageMapper.insert(row);
            }

            conversation.setMessageCount(seq);
            conversation.setLastMessageAt(now);
            conversation.setTitle(title);
            updateMeta(conversation.getId(), seq, now, title);
        });
    }

    /** 上游失败时落一条失败消息,刷新后能看到失败原因 */
    private void persistErrorMessage(AgentConversation conversation, String errorText, String title) {
        int seq = intValue(conversation.getMessageCount()) + 1;
        LocalDateTime now = LocalDateTime.now();
        transactionTemplate.executeWithoutResult(status -> {
            AgentMessage row = new AgentMessage();
            row.setId(idGenerator.nextId());
            row.setConversationId(conversation.getId());
            row.setSeq(seq);
            row.setRole(ChatMessage.ROLE_ASSISTANT.toUpperCase());
            row.setContent(errorText);
            row.setStatus(STATUS_FAILED);
            row.setCreatedAt(now);
            messageMapper.insert(row);

            conversation.setMessageCount(seq);
            conversation.setLastMessageAt(now);
            conversation.setTitle(title);
            updateMeta(conversation.getId(), seq, now, title);
        });
    }

    // ------------------------------------------------------------------
    // 历史重放
    // ------------------------------------------------------------------

    /**
     * 把库里的消息还原成 LLM 能直接吃的消息链。
     *
     * <p>只取最近 {@value #MAX_REPLAY_MESSAGES} 条,并且必须从一条用户提问开始:
     * 截断可能切断某个工具调用的前后半组,留着会让上游报"tool 消息没有对应的调用"。
     * 失败/中断的助手消息也不回放,它们只是给用户看的,喂给模型会污染上下文。</p>
     */
    private List<ChatMessage> loadHistory(long conversationId) {
        List<AgentMessage> rows = messageMapper.selectList(new QueryWrapper<AgentMessage>()
                .eq("conversation_id", conversationId)
                .orderByDesc("seq")
                .last("LIMIT " + MAX_REPLAY_MESSAGES));
        Collections.reverse(rows);

        // 丢掉截断后残留在最前面的半轮
        int from = 0;
        while (from < rows.size() && !ChatMessage.ROLE_USER.equalsIgnoreCase(rows.get(from).getRole())) {
            from++;
        }

        List<ChatMessage> history = new ArrayList<>();
        for (int i = from; i < rows.size(); i++) {
            AgentMessage row = rows.get(i);
            String role = rows.get(i).getRole();
            if (ChatMessage.ROLE_USER.equalsIgnoreCase(role)) {
                if (hasText(row.getContent())) {
                    history.add(new ChatMessage(ChatMessage.ROLE_USER, row.getContent()));
                }
            } else if (ChatMessage.ROLE_ASSISTANT.equalsIgnoreCase(role)) {
                if (hasText(row.getToolCallsJson())) {
                    List<LlmToolCall> calls = readToolCalls(row.getToolCallsJson());
                    if (!calls.isEmpty()) {
                        history.add(ChatMessage.assistantToolCalls(calls));
                    }
                } else if (intValue(row.getStatus()) == STATUS_OK && hasText(row.getContent())) {
                    history.add(new ChatMessage(ChatMessage.ROLE_ASSISTANT, row.getContent()));
                }
            } else if (ChatMessage.ROLE_TOOL.equalsIgnoreCase(role)) {
                history.add(ChatMessage.toolResult(row.getToolCallId(),
                        row.getContent() == null ? "{}" : row.getContent()));
            }
        }
        return history;
    }

    // ------------------------------------------------------------------
    // 展示结构
    // ------------------------------------------------------------------

    /**
     * 把原始消息流合并成前端要的展示消息。
     *
     * <p>一轮工具调用在库里是多行(assistant tool_calls + 若干 tool),展示时要合并成一个助手气泡:
     * 工具卡片进 steps,最终回答进 content。</p>
     */
    private List<AgentDtos.MessageView> toViews(List<AgentMessage> rows) {
        List<AgentDtos.MessageView> views = new ArrayList<>();
        List<AgentDtos.ToolStep> steps = null;
        String turnId = null;
        String lastCreatedAt = "";

        for (AgentMessage row : rows) {
            String role = row.getRole();
            if (ChatMessage.ROLE_USER.equalsIgnoreCase(role)) {
                steps = null;
                turnId = null;
                views.add(new AgentDtos.MessageView(
                        String.valueOf(row.getId()),
                        AgentDtos.MessageView.ROLE_USER,
                        nullToEmpty(row.getContent()),
                        intValue(row.getStatus()),
                        format(row.getCreatedAt()),
                        List.of()));
                continue;
            }
            if (ChatMessage.ROLE_ASSISTANT.equalsIgnoreCase(role) && hasText(row.getToolCallsJson())) {
                if (steps == null) {
                    steps = new ArrayList<>();
                    turnId = String.valueOf(row.getId());
                }
                continue;
            }
            if (ChatMessage.ROLE_TOOL.equalsIgnoreCase(role)) {
                // 没有对应调用的孤立 tool 行属于脏数据,直接跳过
                if (steps != null) {
                    steps.add(toStep(row));
                    lastCreatedAt = format(row.getCreatedAt());
                }
                continue;
            }
            if (ChatMessage.ROLE_ASSISTANT.equalsIgnoreCase(role)) {
                views.add(new AgentDtos.MessageView(
                        turnId == null ? String.valueOf(row.getId()) : turnId,
                        AgentDtos.MessageView.ROLE_ASSISTANT,
                        nullToEmpty(row.getContent()),
                        intValue(row.getStatus()),
                        format(row.getCreatedAt()),
                        steps == null ? List.of() : List.copyOf(steps)));
                steps = null;
                turnId = null;
            }
        }

        // 工具执行后没有最终回答(中断):也要把工具卡片展示出来
        if (steps != null) {
            views.add(new AgentDtos.MessageView(turnId, AgentDtos.MessageView.ROLE_ASSISTANT,
                    "", STATUS_INTERRUPTED, lastCreatedAt, List.copyOf(steps)));
        }
        return views;
    }

    private AgentDtos.ToolStep toStep(AgentMessage row) {
        Map<String, Object> meta = readMap(row.getToolMetaJson());
        return new AgentDtos.ToolStep(
                row.getToolCallId(),
                stringValue(meta.get("name")),
                stringValue(meta.get("label")),
                mapValue(meta.get("args")),
                Boolean.TRUE.equals(meta.get("ok")),
                stringValue(meta.get("summary")));
    }

    // ------------------------------------------------------------------
    // 小工具
    // ------------------------------------------------------------------

    /** 标题占位:压平换行后截断,保证列表里永远有字 */
    private static String previewTitle(String question) {
        String flat = question.replace('\r', ' ').replace('\n', ' ').strip();
        String title = truncate(flat, TITLE_PREVIEW_LENGTH);
        return title == null || title.isBlank() ? "新会话" : title;
    }

    private static String awaitTitle(CompletableFuture<String> future, String fallback) {
        try {
            String title = future.get(TITLE_WAIT_SECONDS, TimeUnit.SECONDS);
            return title == null || title.isBlank() ? fallback : title;
        } catch (TimeoutException | ExecutionException e) {
            log.warn("Agent 标题生成未在 {} 秒内完成,使用占位标题", TITLE_WAIT_SECONDS);
            return fallback;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fallback;
        }
    }

    private static String errorMessage(RuntimeException e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? "生成回答失败，请稍后重试" : message;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("Agent 消息序列化失败", e);
            return "{}";
        }
    }

    private List<LlmToolCall> readToolCalls(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<LlmToolCall>>() {
            });
        } catch (JsonProcessingException e) {
            log.warn("Agent tool_calls 解析失败 json={}", json);
            return List.of();
        }
    }

    private Map<String, Object> readMap(String json) {
        if (!hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (JsonProcessingException e) {
            log.warn("Agent tool_meta 解析失败 json={}", json);
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static int intValue(Integer value) {
        return value == null ? 0 : value;
    }

    private static String format(LocalDateTime time) {
        return time == null ? "" : time.format(STANDARD);
    }

    /**
     * 装饰真实的事件出口:转发的同时记录工具展示信息,流结束后和消息一起落库。
     * 标题、状态这类只影响展示的内容都在这里补齐,AgentService 不关心持久化。
     */
    private static final class RecordingSink implements AgentStreamSink {

        private final AgentStreamSink delegate;
        private final Map<String, ToolMeta> tools = new LinkedHashMap<>();

        private RecordingSink(AgentStreamSink delegate) {
            this.delegate = delegate;
        }

        @Override
        public void conversation(String conversationId, String title) {
            delegate.conversation(conversationId, title);
        }

        @Override
        public void status(String text) {
            delegate.status(text);
        }

        @Override
        public void toolCall(String id, String name, String label, Map<String, Object> args) {
            tools.put(id, new ToolMeta(name, label, args == null ? Map.of() : args, false, null));
            delegate.toolCall(id, name, label, args);
        }

        @Override
        public void toolResult(String id, String name, boolean ok, String summary) {
            // 先记录再转发:转发可能因为客户端断开直接抛异常,记录不能丢
            ToolMeta previous = tools.get(id);
            tools.put(id, new ToolMeta(name, previous == null ? name : previous.label(),
                    previous == null ? Map.of() : previous.args(), ok, summary));
            delegate.toolResult(id, name, ok, summary);
        }

        @Override
        public void delta(String text) {
            delegate.delta(text);
        }

        /** 落库用:内容和展示字段都转成普通 Map,方便统一序列化 */
        private Map<String, Object> metaOf(String toolCallId) {
            ToolMeta meta = tools.get(toolCallId);
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("name", meta == null ? "" : meta.name());
            map.put("label", meta == null ? "" : meta.label());
            map.put("args", meta == null ? Map.of() : meta.args());
            map.put("ok", meta != null && meta.ok());
            map.put("summary", meta == null ? "" : nullToEmpty(meta.summary()));
            return map;
        }

        private record ToolMeta(String name, String label, Map<String, Object> args,
                                boolean ok, String summary) {
        }
    }
}
