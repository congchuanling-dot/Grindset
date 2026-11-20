# 首页 Agent 会话持久化 · 技术设计

> 版本：v0.1
> 作者：从传领
> 日期：2026-11-20
> 关联：[AGENTS.md](../AGENTS.md)、[AI面试-技术设计](AI面试-技术设计.md)（SSE 与消息线程做法）、[面试复习闭环-PRD](面试复习闭环-PRD.md)
> 一句话：新增 `agent_conversations` / `agent_messages` 两张表，把首页 Agent 的完整消息流落库；前端增加会话侧边栏，支持新建、历史切换、刷新恢复、重命名和删除。

---

## 1. 背景与目标

### 1.1 背景

改造前首页 Agent 有两处问题：

- 前端 [ChatPage.tsx](../frontend/src/pages/ChatPage.tsx) 的消息只存在 React state，刷新页面即丢
- 后端 `/api/agent/chat` 完全无状态，每轮由前端把完整历史传上来，服务端不保存任何内容

### 1.2 目标

| 能力 | 说明 |
|---|---|
| 会话列表 | 首页左侧展示当前用户的历史会话，按最后消息时间倒序 |
| 新建会话 | 不产生空会话，发送第一条消息时懒创建 |
| 刷新恢复 | 当前会话写入 URL `?c=<id>`，刷新后从库里恢复 |
| 历史切换 | 点击列表项加载完整对话（含当时的工具调用卡片） |
| 重命名 / 删除 | 支持手动改名、删除会话及其全部消息 |
| 上下文重放 | 继续旧会话时，后端从库里重放历史，前端只传本次提问 |
| 异常可见 | 模型失败、客户端中断都落库，刷新后能看到失败原因或已生成的部分 |

### 1.3 非目标

- 多实例部署的分布式锁（当前项目是单实例假设）
- 会话全文搜索、分页游标、归档
- 长对话 LLM 摘要压缩（继续用现有整组滑窗裁剪）
- 自动生成会话标题之外的 AI 会话管理能力

---

## 2. 方案选型

### 2.1 存储选型

| 方案 | 结论 |
|---|---|
| **MySQL：会话表 + 消息表（追加写）** | 采用。与现有 MyBatis-Plus 体系一致，支持历史列表、恢复、删除，后续可扩展搜索 |
| 单行 JSON 存整段上下文 | 放弃。每次读改写整行，行越来越长，并发写冲突，无法分页 |
| Redis 作为主存储 | 放弃。重启/淘汰会丢历史，无法跨设备查看 |
| MySQL + Redis 热缓存 | 暂不做。当前写入量极低，先不引入一致性负担 |

### 2.2 写放大控制

一轮对话的写库次数固定为：

```
开始：INSERT 用户消息 + UPDATE 会话元数据
结束：批量 INSERT 本轮助手/工具消息 + UPDATE 会话元数据
```

**不在流式过程中逐 delta 写库**，否则一条回答几百个 delta 会变成几百次 UPDATE。

---

## 3. 数据模型

建表脚本：[sql/011_agent_chat_schema.sql](../sql/011_agent_chat_schema.sql)

### 3.1 agent_conversations

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT | 雪花 ID |
| `user_id` | BIGINT | 所属用户 |
| `title` | VARCHAR(64) | 标题：先截断首问占位，首轮结束后用 LLM 生成 ≤15 字 |
| `message_count` | INT | 消息条数，同时作为下一次写入的 `seq` 起点 |
| `last_message_at` | DATETIME | 列表排序字段 |
| `created_at` / `updated_at` | DATETIME | 创建/更新时间（代码显式写 `created_at`，避免数据库时区差异） |

索引：`KEY idx_user_last (user_id, last_message_at)`。

### 3.2 agent_messages

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | BIGINT | 雪花 ID |
| `conversation_id` | BIGINT | 所属会话 |
| `seq` | INT | 会话内顺序，从 1 开始 |
| `role` | VARCHAR(16) | `USER` / `ASSISTANT` / `TOOL` |
| `content` | TEXT | 用户提问、助手回答、工具结果 JSON 字符串 |
| `tool_calls_json` | JSON | assistant 发起工具调用时：`[{id,name,arguments}]` |
| `tool_call_id` | VARCHAR(64) | TOOL 消息对应的调用 ID |
| `tool_meta_json` | JSON | TOOL 消息展示信息：`{name,label,args,ok,summary}` |
| `status` | TINYINT | 仅 assistant：`0` 正常 / `2` 失败 / `3` 中断 |
| `created_at` | DATETIME | 创建时间 |

索引：`UNIQUE KEY uk_conv_seq (conversation_id, seq)`、`KEY idx_conv_created (conversation_id, created_at)`。

约定：

- 表之间没有物理外键，删除会话时由代码先删消息
- `tool_calls_json` 存模型返回的**原始 arguments 字符串**，专门用于精确重放
- `tool_meta_json` 存解析后的参数和展示字段，专门用于历史卡片渲染
- 失败/中断消息也落库，但重放给模型时会跳过

---

## 4. 消息流与重放

### 4.1 一轮消息在库里的形态

```
seq 1  USER       "分析一下我的薄弱点"
seq 2  ASSISTANT  tool_calls=[{id:call_1,...}]        (content 为空)
seq 3  TOOL       tool_call_id=call_1, meta={...}
seq 4  TOOL       tool_call_id=call_2, meta={...}
seq 5  ASSISTANT  "先给结论：……"                       (最终回答)
```

模型可能连续多轮调用工具，每轮都会产生一组 `assistant(tool_calls) + tool...`，最后再跟一条普通 assistant。

### 4.2 重放规则

[AgentConversationService.loadHistory](../backend/src/main/java/com/grindset/backend/service/agent/AgentConversationService.java)：

1. 按 `seq DESC` 取最近 **200** 条，再反转为升序
2. 丢掉截断后残留在最前面的半轮，保证第一条是 `USER`
3. 映射规则：
   - `USER` → `ChatMessage.user`
   - `ASSISTANT + tool_calls_json` → `ChatMessage.assistantToolCalls`
   - `TOOL` → `ChatMessage.toolResult`
   - 普通 `ASSISTANT` 且 `status = 0` → `ChatMessage.assistant`
   - 失败/中断的 assistant **不回放**，避免错误文案污染上下文
4. 交给 [AgentService](../backend/src/main/java/com/grindset/backend/service/agent/AgentService.java) 的 `trimContext`：按字符数 24k 上限、按完整 turn 整组丢弃，保留最近 3 组

### 4.3 展示聚合

库里一轮工具调用是多行，前端需要的是一个助手气泡：

- assistant 工具调用 + 后续 tool 行 → 合并为一个 `AgentMessageView`
- 工具卡片放 `steps`（来自 `tool_meta_json`），最终回答放 `content`
- 如果这一轮在工具执行后就中断、没有最终回答，也输出一个 `status=3` 的空回答气泡，带上已有工具卡片
- 孤立 tool 行（没有前置 tool_calls）属于脏数据，直接忽略

---

## 5. 写入时机与异常处理

### 5.1 正常一轮

```
1. 校验 provider/model/message
2. 无 conversationId → 新建会话（标题=首问截断 15 字）
3. 加会话锁，锁内重新确认会话存在
4. 读历史（必须在写入本轮提问之前）
5. 短事务：INSERT 用户消息 + UPDATE message_count/last_message_at
6. 发送 SSE conversation 事件
7. 标题生成与回答并行
8. 流式生成，不写库
9. 短事务：批量 INSERT 本轮消息 + UPDATE 会话元数据
10. 释放锁
```

### 5.2 模型失败

`AgentService` 抛出异常后：

- 落一条 `ASSISTANT` 消息，`status=2`，内容为错误文案
- 异常继续抛给 Controller，通过 SSE `error` 事件返回前端
- 刷新后仍能看到失败原因

### 5.3 客户端中断

点停止、关页面、刷新都会断开 SSE：

- `AgentService` 捕获 `AgentStreamAbortedException`，带着已累积的正文正常返回
- 落库为 `status=3`，已生成的部分保留
- 如果刚好在工具执行中断，**只保留到最后一个完整工具组**；残缺的 `assistant(tool_calls)` 半组直接丢弃，否则下次重放会被上游判为非法消息序列
- 一个字都没生成时，落一条空的 `status=3` 消息，前端显示"生成已中断"

### 5.4 删除与生成的竞态

回归测试实际发现过：生成过程中删除会话，流结束后仍会往已删除的会话插消息，产生孤儿数据。

处理：

- `rename` / `delete` 先拿会话锁，生成中直接返回 `409`
- `chat` 拿到锁后重新查一次会话是否存在，堵住"读到会话 → 被删除 → 再写消息"的窗口
- 会话元数据更新改成定点更新（`message_count` / `last_message_at` / `title`），不再整行覆盖，避免把用户刚改的标题写回旧值

---

## 6. 标题生成

[AgentTitleService](../backend/src/main/java/com/grindset/backend/service/agent/AgentTitleService.java)

- 仅首轮生成；后续轮次沿用现有标题
- 与回答**并行**执行，回答结束时最多等待 3 秒
- 提示词要求只输出标题、不超过 15 字
- 结果做归一化：压平换行、去首尾引号/标点、截断 15 字
- 失败或超时回退到"首问截断 15 字"的占位标题，不影响主流程

---

## 7. 接口

### 7.1 会话管理

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/agent/conversations` | 列表，最多 100 条，按 `last_message_at` 倒序 |
| GET | `/api/agent/conversations/{id}` | 详情 + 聚合后的消息 |
| PATCH | `/api/agent/conversations/{id}` | 重命名 `{title}`，1~64 字 |
| DELETE | `/api/agent/conversations/{id}` | 删除会话及全部消息 |

归属校验失败统一 `404`；重命名为空/超长 `400`；生成中改删 `409`。

### 7.2 对话

`POST /api/agent/chat`

```json
{
  "conversationId": "382164794729304064",
  "provider": "deepseek",
  "model": "deepseek-flash",
  "message": "分析一下我的薄弱点"
}
```

- `conversationId` 为空：懒创建新会话，历史为空
- `conversationId` 有值：校验归属，历史从库里重放，前端**不再上传完整历史**
- 先校验 provider/model 可用，再落用户提问

### 7.3 SSE 事件

| 事件 | 数据 | 时机 |
|---|---|---|
| `conversation` | `{conversationId,title}` | 首帧，前端据此写 URL、更新侧边栏 |
| `status` | `{text}` | 阶段提示 |
| `tool_call` | `{id,name,label,args}` | 模型决定调用工具 |
| `tool_result` | `{id,name,ok,summary}` | 工具执行完成 |
| `delta` | `{text}` | 最终回答增量 |
| `done` | `{conversationId,title}` | 结束，带最新标题 |
| `error` | `{message}` | 业务/上游错误 |

注意：SSE 响应头已经发出，参数错误和并发冲突也是 **HTTP 200 + `error` 事件**，只有非流式的会话管理接口才返回真实的 400/404/409。

---

## 8. 并发与一致性

### 8.1 会话级锁

[AgentConversationLocks](../backend/src/main/java/com/grindset/backend/service/agent/AgentConversationLocks.java)：

```java
ConcurrentHashMap<Long, ReentrantLock>
```

与面试模块的 `InterviewSessionLocks` 同一套思路，一个会话一把锁：

- `chat`：`tryLock()` 失败 → "该会话正在生成回答，请稍候"
- `rename` / `delete`：`tryLock()` 失败 → `409`
- `finally` 中释放

### 8.2 一致性不变量

- `agent_conversations.message_count` = 该会话实际消息行数 = 最大 `seq`
- `seq` 从 1 开始连续无空洞（唯一索引兜底）
- 不存在"没有对应 tool_calls 的 tool 行"，也不存在缺结果的 tool_calls 组
- 不存在引用已删除会话的孤儿消息

### 8.3 限制

- 进程内锁，只适用于单实例；多实例需要 Redis 锁或数据库行锁
- `locks` Map 不清理，每聊过一个会话多一个锁对象，量级很小但不是严格无泄漏

---

## 9. 前端设计

### 9.1 布局

[ChatPage.tsx](../frontend/src/pages/ChatPage.tsx) 内部左右分栏：

- 左侧 260px 会话面板：新建会话、历史列表、hover 重命名/删除
- 窄屏（`< md`）时面板变成抽屉，用顶部按钮打开
- 右侧保留原有对话区和输入框，不改全局 `AppLayout`

### 9.2 会话选择与 URL

- 当前会话使用 `?c=<id>`，刷新自动恢复
- 新建会话：清空 URL 和消息，不请求后端（懒创建）
- 发送第一条消息：SSE `conversation` 事件返回 ID，前端写入 URL 并插入列表顶部
- 历史切换：`?c=` 变化触发详情加载

**关键修复**：新建会话发送第一条消息时，`onConversation` 会同时更新 `activeId` 和 URL，但路由更新可能先于 state 提交触发加载 effect，读到旧的 `activeId=null`，误把"只落了用户提问"的库里快照覆盖到本地消息，导致正在流式输出的助手气泡消失。现在用 `activeIdRef` 同步保存当前会话 ID，effect 只用 ref 判断，不再依赖可能滞后的 state。

### 9.3 交互约束

- 生成中禁止切换会话、新建、重命名、删除
- 历史工具卡片按 `ok` 映射为 `done` / `failed`
- 失败消息红色展示，中断消息显示"生成已中断"
- 每轮结束后重新拉一次会话列表，校准标题、排序和消息数

---

## 10. 测试与验收

| 测试组 | 结果 | 覆盖 |
|---|---|---|
| 接口主流程 | 34/34 | 新建、多轮重放、工具落库与合并、并发保护、重命名、删除、越权 404、`/api/chat` 回归 |
| 参数与数据一致性 | 10/10 | 空消息、未知厂商、超长消息、不存在会话、标题 ≤15 字、`message_count` 一致、无孤儿消息、工具组完整 |
| 流式中断 | 8/8 | 看到 delta 后强杀连接，正文保留、`status=3`、`seq` 连续、无残缺工具组 |
| 生成中删改竞态 | 10/10 | 生成中重命名/删除 409、被拒后会话仍在、结束后可删、无孤儿消息 |
| 浏览器 E2E | 通过 | 新建、发送、实时渲染、刷新恢复、历史切换、重命名、删除 |

回归过程中发现并修复的两个问题：

1. **生成中删除产生孤儿消息**：删除和生成没有互斥
2. **前端新建会话消息被清空**：URL effect 用滞后的 `activeId` 误判成切换会话

---

## 11. 已知限制与后续演进

| 项 | 当前做法 | 后续 |
|---|---|---|
| 多实例 | 进程内 `ReentrantLock` | 换 Redis 分布式锁或数据库 `FOR UPDATE` |
| 长对话 | 最近 200 条 + 24k 字符整组裁剪 | LLM 摘要压缩（代码已有 TODO） |
| 列表 | 最多 100 条，无分页 | 游标分页 + 关键词搜索 |
| 标题 | 首轮 LLM 生成 ≤15 字 | 支持用户手动改名（已具备）；自动标题不覆盖手动改名 |
| 会话查找 | 无 | MySQL 全文索引 / ngram |
| 锁对象清理 | Map 常驻 | 引用计数或无锁化 |

---

## 12. 变更文件清单

### 12.1 新增

| 文件 | 职责 |
|---|---|
| `sql/011_agent_chat_schema.sql` | 两张表 DDL |
| `entity/AgentConversation.java`、`entity/AgentMessage.java` | 实体 |
| `mapper/AgentConversationMapper.java`、`mapper/AgentMessageMapper.java` | Mapper |
| `dto/AgentChatRequest.java` | 对话请求 |
| `dto/AgentChatResult.java` | 一轮结束结果 |
| `dto/AgentConversationSummary.java`、`dto/AgentConversationDetail.java`、`dto/AgentMessageView.java` | 列表/详情/展示消息 |
| `dto/AgentRenameRequest.java` | 重命名请求 |
| `service/agent/AgentConversationService.java` | 会话 CRUD、落库、重放、展示聚合 |
| `service/agent/AgentConversationLocks.java` | 会话级锁 |
| `service/agent/AgentTitleService.java` | LLM 标题生成 |
| `service/agent/AgentTurn.java`、`AgentStreamAbortedException.java` | 一轮消息结果、中断异常 |
| `controller/AgentConversationController.java` | 会话管理接口 |

### 12.2 修改

| 文件 | 改动 |
|---|---|
| [AgentService.java](../backend/src/main/java/com/grindset/backend/service/agent/AgentService.java) | 不再自己拼历史；返回本轮完整消息流；中断时保留半成品 |
| [AgentController.java](../backend/src/main/java/com/grindset/backend/controller/AgentController.java) | 改用 `AgentChatRequest`；SSE 增加 `conversation` 事件；`done` 带标题 |
| [AgentStreamSink.java](../backend/src/main/java/com/grindset/backend/service/agent/AgentStreamSink.java) | 增加 `conversation()` 默认方法 |
| [GlobalExceptionHandler.java](../backend/src/main/java/com/grindset/backend/common/GlobalExceptionHandler.java) | 客户端断连的 `IOException` 安静处理，避免向 SSE 响应写 JSON |
| [agent.ts](../frontend/src/api/agent.ts) | 会话 CRUD + 新 SSE 事件 |
| [ChatPage.tsx](../frontend/src/pages/ChatPage.tsx) | 会话侧边栏、URL 恢复、`activeIdRef` 修复 |
| [AGENTS.md](../AGENTS.md) | 初始化脚本、SSE 事件、接口清单、表清单、文档索引 |

---

## 13. 初始化

新环境需要执行（可重复执行）：

```bash
mysql -uroot -p123456 < sql/011_agent_chat_schema.sql
```

改造前没有任何 Agent 会话数据，因此不需要数据迁移。
