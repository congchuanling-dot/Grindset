package com.grindset.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.grindset.backend.common.AiBizType;
import com.grindset.backend.common.SnowflakeIdGenerator;
import com.grindset.backend.common.constant.TimeFormats;
import com.grindset.backend.dto.UsageDtos;
import com.grindset.backend.entity.AiUsageRecord;
import com.grindset.backend.entity.LlmChannel;
import com.grindset.backend.entity.LlmModel;
import com.grindset.backend.llm.LlmTarget;
import com.grindset.backend.llm.LlmUsage;
import com.grindset.backend.llm.LlmUsageContext;
import com.grindset.backend.mapper.AiUsageRecordMapper;
import com.grindset.backend.mapper.LlmChannelMapper;
import com.grindset.backend.mapper.LlmModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI 用量记账与查询。
 *
 * <p>记账只依赖 LlmRouter 的统一埋点:所有模型调用都经过路由,
 * 这里不必关心调用方是 Agent、面试还是简历解析。写库失败只记日志,
 * 绝不打断模型调用主流程。</p>
 *
 * <p>成本 = 输入 token × 输入单价 + 缓存命中 token × 缓存单价 + 输出 token × 输出单价,
 * 单价单位为「元/千 token」,在写入时快照。</p>
 */
@Service
public class AiUsageService {

    private static final Logger log = LoggerFactory.getLogger(AiUsageService.class);

    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);
    private static final int COST_SCALE = 8;
    private static final int MAX_RANGE_DAYS = 90;
    private static final int MAX_PAGE_SIZE = 100;

    private final AiUsageRecordMapper usageMapper;
    private final LlmChannelMapper channelMapper;
    private final LlmModelMapper modelMapper;
    private final SnowflakeIdGenerator idGenerator;

    public AiUsageService(AiUsageRecordMapper usageMapper, LlmChannelMapper channelMapper,
                          LlmModelMapper modelMapper, SnowflakeIdGenerator idGenerator) {
        this.usageMapper = usageMapper;
        this.channelMapper = channelMapper;
        this.modelMapper = modelMapper;
        this.idGenerator = idGenerator;
    }

    // ------------------------------------------------------------------
    // 记账
    // ------------------------------------------------------------------

    /** 记录一次模型调用;任何异常都吞掉并打日志,不影响调用方 */
    public void record(LlmUsageContext context, LlmTarget target, String model, LlmUsage usage) {
        if (context == null || target == null) {
            return;
        }
        try {
            LlmUsage actual = usage == null ? LlmUsage.EMPTY : usage;
            Pricing pricing = pricingOf(target.providerId(), model);

            AiUsageRecord record = new AiUsageRecord();
            record.setId(idGenerator.nextId());
            record.setUserId(context.userId());
            record.setBizType(context.bizType().name());
            record.setBizRef(context.bizRef());
            record.setProviderId(target.providerId());
            record.setModelId(model == null ? "" : model);
            record.setKeyId(target.keyId());
            record.setInputTokens(actual.inputTokens());
            record.setOutputTokens(actual.outputTokens());
            record.setCachedTokens(actual.cachedTokens());
            record.setEstimated(actual.estimated() ? 1 : 0);
            record.setInputPrice(pricing.input());
            record.setCachedInputPrice(pricing.cached());
            record.setOutputPrice(pricing.output());
            record.setCost(calculateCost(actual, pricing));
            usageMapper.insert(record);
        } catch (Exception e) {
            log.warn("AI 用量记账失败 userId={} biz={} provider={} model={}",
                    context.userId(), context.bizType(), target.providerId(), model, e);
        }
    }

    private static BigDecimal calculateCost(LlmUsage usage, Pricing pricing) {
        int cached = Math.min(Math.max(usage.cachedTokens(), 0), usage.inputTokens());
        int normalInput = Math.max(usage.inputTokens() - cached, 0);
        return BigDecimal.valueOf(normalInput).multiply(pricing.input())
                .add(BigDecimal.valueOf(cached).multiply(pricing.cached()))
                .add(BigDecimal.valueOf(usage.outputTokens()).multiply(pricing.output()))
                .divide(THOUSAND, COST_SCALE, RoundingMode.HALF_UP);
    }

    /** 按 provider + model 找单价;渠道/模型不存在或没配价时按 0 记 */
    private Pricing pricingOf(String providerId, String model) {
        List<LlmChannel> channels = channelMapper.selectList(new QueryWrapper<LlmChannel>()
                .eq("provider_id", providerId)
                .eq("enabled", 1));
        for (LlmChannel channel : channels) {
            LlmModel row = modelMapper.selectOne(new QueryWrapper<LlmModel>()
                    .eq("channel_id", channel.getId())
                    .eq("model_id", model)
                    .last("LIMIT 1"));
            if (row != null) {
                BigDecimal input = nz(row.getInputPrice());
                // 缓存单价没配时按输入原价算,避免缓存命中被当成免费
                BigDecimal cached = nz(row.getCachedInputPrice());
                if (cached.signum() <= 0) {
                    cached = input;
                }
                return new Pricing(input, cached, nz(row.getOutputPrice()));
            }
        }
        return Pricing.ZERO;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    public UsageDtos.Summary summary(long userId, int days) {
        int range = Math.min(Math.max(days, 1), MAX_RANGE_DAYS);
        LocalDateTime from = LocalDate.now().minusDays(range - 1L).atStartOfDay();

        Map<String, Object> totals = usageMapper.totals(userId, from);
        long input = longOf(totals, "in_tokens");
        long output = longOf(totals, "out_tokens");
        BigDecimal cost = decimalOf(totals, "cost");

        List<UsageDtos.BizUsage> byBizType = new ArrayList<>();
        for (Map<String, Object> row : usageMapper.byBizType(userId, from)) {
            String bizType = String.valueOf(row.get("biz_type"));
            byBizType.add(new UsageDtos.BizUsage(bizType, AiBizType.labelOf(bizType),
                    longOf(row, "calls"), longOf(row, "in_tokens"),
                    longOf(row, "out_tokens"), decimalOf(row, "cost")));
        }

        List<UsageDtos.ModelUsage> byModel = new ArrayList<>();
        for (Map<String, Object> row : usageMapper.byModel(userId, from)) {
            byModel.add(new UsageDtos.ModelUsage(String.valueOf(row.get("provider_id")),
                    String.valueOf(row.get("model_id")), longOf(row, "calls"),
                    longOf(row, "in_tokens"), longOf(row, "out_tokens"), decimalOf(row, "cost")));
        }

        List<UsageDtos.DailyUsage> daily = new ArrayList<>();
        for (Map<String, Object> row : usageMapper.daily(userId, from)) {
            daily.add(new UsageDtos.DailyUsage(String.valueOf(row.get("day")),
                    longOf(row, "in_tokens"), longOf(row, "out_tokens"), decimalOf(row, "cost")));
        }

        return new UsageDtos.Summary(longOf(totals, "calls"), input, output, input + output,
                cost, byBizType, byModel, daily);
    }

    public UsageDtos.RecordPage records(long userId, int page, int size) {
        int current = Math.max(page, 1);
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        long total = usageMapper.countByUser(userId);
        List<AiUsageRecord> rows = total == 0
                ? List.of()
                : usageMapper.pageByUser(userId, pageSize, (long) (current - 1) * pageSize);

        List<UsageDtos.RecordView> items = new ArrayList<>(rows.size());
        for (AiUsageRecord row : rows) {
            items.add(new UsageDtos.RecordView(String.valueOf(row.getId()), row.getBizType(),
                    AiBizType.labelOf(row.getBizType()), row.getProviderId(), row.getModelId(),
                    intOf(row.getInputTokens()), intOf(row.getOutputTokens()),
                    intOf(row.getCachedTokens()), intOf(row.getEstimated()) == 1,
                    row.getCost() == null ? BigDecimal.ZERO : row.getCost(),
                    row.getCreatedAt() == null ? "" : TimeFormats.STANDARD.format(row.getCreatedAt())));
        }
        return new UsageDtos.RecordPage(items, total, current, pageSize);
    }

    private static long longOf(Map<String, Object> row, String key) {
        if (row == null) {
            return 0L;
        }
        Object value = row.get(key);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static BigDecimal decimalOf(Map<String, Object> row, String key) {
        if (row == null) {
            return BigDecimal.ZERO;
        }
        Object value = row.get(key);
        return value instanceof BigDecimal decimal ? decimal : BigDecimal.ZERO;
    }

    private static int intOf(Integer value) {
        return value == null ? 0 : value;
    }

    /** 单价快照,单位元/千 token */
    private record Pricing(BigDecimal input, BigDecimal cached, BigDecimal output) {

        static final Pricing ZERO = new Pricing(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
