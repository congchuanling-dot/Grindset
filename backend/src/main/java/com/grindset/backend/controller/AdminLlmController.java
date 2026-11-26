package com.grindset.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.grindset.backend.common.AdminGuard;
import com.grindset.backend.common.BizException;
import com.grindset.backend.common.ErrorCode;
import com.grindset.backend.dto.ApiResponse;
import com.grindset.backend.entity.LlmChannel;
import com.grindset.backend.entity.LlmKey;
import com.grindset.backend.entity.LlmModel;
import com.grindset.backend.mapper.LlmChannelMapper;
import com.grindset.backend.mapper.LlmKeyMapper;
import com.grindset.backend.mapper.LlmModelMapper;
import com.grindset.backend.service.BalanceService;
import com.grindset.backend.service.LlmKeyService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.grindset.backend.common.constant.LlmEnabledStatus.ENABLED;
import static com.grindset.backend.common.constant.LlmKeyStatus.NORMAL;
import static com.grindset.backend.common.constant.TimeFormats.STANDARD;

/**
 * 模型服务管理接口(需要 X-Admin-Token)。
 * 渠道/模型本身直接改表即可,这里只处理需要加密或需要即时生效的操作:加 key、启停 key、维护余额。
 */
@RestController
@RequestMapping("/api/admin/llm")
public class AdminLlmController {

    /** 单价上限(元/百万 token),防止误填把成本算爆 */
    private static final BigDecimal MAX_PRICE_PER_MILLION = new BigDecimal("100000");

    private final AdminGuard guard;
    private final LlmChannelMapper channelMapper;
    private final LlmKeyMapper keyMapper;
    private final LlmModelMapper modelMapper;
    private final LlmKeyService keyService;
    private final BalanceService balanceService;

    public AdminLlmController(AdminGuard guard, LlmChannelMapper channelMapper, LlmKeyMapper keyMapper,
                              LlmModelMapper modelMapper, LlmKeyService keyService,
                              BalanceService balanceService) {
        this.guard = guard;
        this.channelMapper = channelMapper;
        this.keyMapper = keyMapper;
        this.modelMapper = modelMapper;
        this.keyService = keyService;
        this.balanceService = balanceService;
    }

    /** 渠道列表:含余额、key 掩码与健康状态 */
    @GetMapping("/channels")
    public ApiResponse<List<ChannelView>> channels(
            @RequestHeader(name = "X-Admin-Token", required = false) String token) {
        guard.check(token);

        List<LlmChannel> channels = channelMapper.selectList(new QueryWrapper<LlmChannel>()
                .orderByDesc("priority").orderByAsc("id"));
        List<LlmKey> keys = keyMapper.selectList(new QueryWrapper<LlmKey>()
                .orderByAsc("channel_id").orderByAsc("id"));

        Map<Long, List<KeyView>> keysByChannel = new LinkedHashMap<>();
        for (LlmKey key : keys) {
            keysByChannel.computeIfAbsent(key.getChannelId(), id -> new ArrayList<>())
                    .add(new KeyView(key.getId(), key.getKeyMask(),
                            key.getStatus() == null ? NORMAL : key.getStatus(),
                            key.getFailCount() == null ? 0 : key.getFailCount(),
                            format(key.getCooldownUntil()), format(key.getLastUsedAt())));
        }

        List<ChannelView> views = new ArrayList<>(channels.size());
        for (LlmChannel channel : channels) {
            views.add(new ChannelView(channel.getId(), channel.getProviderId(), channel.getName(),
                    channel.getBaseUrl(), channel.getPriority() == null ? 0 : channel.getPriority(),
                    channel.getEnabled() != null && channel.getEnabled() == ENABLED,
                    channel.getBalance(), channel.getBalanceCurrency(), channel.getBalanceSource(),
                    format(channel.getBalanceUpdatedAt()),
                    keysByChannel.getOrDefault(channel.getId(), List.of())));
        }
        return ApiResponse.ok(views);
    }

    /** 给渠道加一把 key,返回掩码 */
    @PostMapping("/channels/{id}/keys")
    public ApiResponse<Map<String, String>> addKey(
            @RequestHeader(name = "X-Admin-Token", required = false) String token,
            @PathVariable long id, @RequestBody AddKeyRequest req) {
        guard.check(token);
        requireChannel(id);
        return ApiResponse.ok(Map.of("mask", keyService.addKey(id, req.key())));
    }

    /** 启用(0) / 禁用(2) 某把 key */
    @PatchMapping("/keys/{id}")
    public ApiResponse<Void> updateKey(
            @RequestHeader(name = "X-Admin-Token", required = false) String token,
            @PathVariable long id, @RequestBody UpdateKeyRequest req) {
        guard.check(token);
        if (req.status() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "status 不能为空");
        }
        keyService.updateStatus(id, req.status());
        return ApiResponse.ok(null);
    }

    /** 手工维护余额(适用于没有余额查询接口的厂商),会把来源改为 MANUAL */
    @PutMapping("/channels/{id}/balance")
    public ApiResponse<Void> updateBalance(
            @RequestHeader(name = "X-Admin-Token", required = false) String token,
            @PathVariable long id, @RequestBody BalanceRequest req) {
        guard.check(token);
        if (req.amount() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "余额不能为空");
        }
        requireChannel(id);

        LlmChannel patch = new LlmChannel();
        patch.setId(id);
        patch.setBalance(req.amount());
        if (req.currency() != null && !req.currency().isBlank()) {
            patch.setBalanceCurrency(req.currency().strip().toUpperCase());
        }
        patch.setBalanceSource("MANUAL");
        patch.setBalanceUpdatedAt(LocalDateTime.now());
        channelMapper.updateById(patch);
        return ApiResponse.ok(null);
    }

    /** 立即拉取一次余额(目前仅 DeepSeek 支持) */
    @PostMapping("/channels/{id}/balance/refresh")    public ApiResponse<Map<String, Object>> refreshBalance(
            @RequestHeader(name = "X-Admin-Token", required = false) String token,
            @PathVariable long id) {
        guard.check(token);
        LlmChannel channel = requireChannel(id);
        if (!"deepseek".equals(channel.getProviderId())) {
            throw new BizException(ErrorCode.BAD_REQUEST, "该厂商暂不支持自动查询余额,请手工维护");
        }
        if (!balanceService.refresh(channel)) {
            throw new BizException(ErrorCode.BAD_GATEWAY, "查询余额失败,请检查 key 与网络后重试");
        }
        LlmChannel latest = channelMapper.selectById(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("balance", latest.getBalance());
        data.put("currency", latest.getBalanceCurrency());
        data.put("updatedAt", format(latest.getBalanceUpdatedAt()));
        return ApiResponse.ok(data);
    }

    /** 模型单价列表,价格按「元/百万 token」返回,与厂商官网口径一致 */
    @GetMapping("/models")
    public ApiResponse<List<ModelPriceView>> models(
            @RequestHeader(name = "X-Admin-Token", required = false) String token) {
        guard.check(token);

        List<LlmChannel> channels = channelMapper.selectList(new QueryWrapper<LlmChannel>()
                .orderByDesc("priority").orderByAsc("id"));
        Map<Long, LlmChannel> channelById = new LinkedHashMap<>();
        for (LlmChannel channel : channels) {
            channelById.put(channel.getId(), channel);
        }

        List<LlmModel> models = modelMapper.selectList(new QueryWrapper<LlmModel>()
                .orderByAsc("channel_id").orderByAsc("sort"));
        List<ModelPriceView> views = new ArrayList<>(models.size());
        for (LlmModel model : models) {
            LlmChannel channel = channelById.get(model.getChannelId());
            if (channel == null) {
                continue;
            }
            views.add(new ModelPriceView(String.valueOf(model.getId()), channel.getProviderId(),
                    channel.getName(), model.getModelId(), model.getDisplayName(),
                    model.getEnabled() != null && model.getEnabled() == ENABLED,
                    perMillion(model.getInputPrice()), perMillion(model.getCachedInputPrice()),
                    perMillion(model.getOutputPrice())));
        }
        return ApiResponse.ok(views);
    }

    /** 更新模型单价;入参为元/百万 token,落库转成元/千 token */
    @PutMapping("/models/{id}/prices")
    public ApiResponse<Void> updateModelPrices(
            @RequestHeader(name = "X-Admin-Token", required = false) String token,
            @PathVariable long id, @RequestBody ModelPriceRequest req) {
        guard.check(token);
        LlmModel model = modelMapper.selectById(id);
        if (model == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "模型不存在");
        }

        LlmModel patch = new LlmModel();
        patch.setId(id);
        patch.setInputPrice(toPerThousand(req.inputPrice(), "输入单价"));
        patch.setCachedInputPrice(toPerThousand(req.cachedInputPrice(), "缓存命中单价"));
        patch.setOutputPrice(toPerThousand(req.outputPrice(), "输出单价"));
        modelMapper.updateById(patch);
        return ApiResponse.ok(null);
    }

    /** 库里的单价是元/千 token,返回给前端时换算成元/百万 token */
    private static BigDecimal perMillion(BigDecimal perThousand) {
        return perThousand == null ? BigDecimal.ZERO : perThousand.multiply(BigDecimal.valueOf(1000));
    }

    /** 前端按元/百万 token 提交,落库除以 1000;负数或超范围直接拒绝 */
    private static BigDecimal toPerThousand(BigDecimal perMillion, String label) {
        if (perMillion == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, label + "不能为空");
        }
        if (perMillion.signum() < 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, label + "不能为负数");
        }
        if (perMillion.compareTo(MAX_PRICE_PER_MILLION) > 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, label + "超出合理范围");
        }
        return perMillion.divide(BigDecimal.valueOf(1000), 6, RoundingMode.HALF_UP);
    }

    private LlmChannel requireChannel(long id) {
        LlmChannel channel = channelMapper.selectById(id);
        if (channel == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "渠道不存在");
        }
        return channel;
    }

    private static String format(LocalDateTime time) {
        return time == null ? null : time.format(STANDARD);
    }

    public record ChannelView(long id, String providerId, String name, String baseUrl, int priority,
                              boolean enabled, BigDecimal balance, String currency, String source,
                              String balanceUpdatedAt, List<KeyView> keys) {
    }

    public record KeyView(long id, String mask, int status, int failCount,
                          String cooldownUntil, String lastUsedAt) {
    }

    public record AddKeyRequest(String key) {
    }

    public record UpdateKeyRequest(Integer status) {
    }

    public record BalanceRequest(BigDecimal amount, String currency) {
    }

    public record ModelPriceView(String id, String providerId, String channelName, String modelId,
                                 String displayName, boolean enabled, BigDecimal inputPrice,
                                 BigDecimal cachedInputPrice, BigDecimal outputPrice) {
    }

    /** 单价入参,单位统一为元/百万 token */
    public record ModelPriceRequest(BigDecimal inputPrice, BigDecimal cachedInputPrice,
                                    BigDecimal outputPrice) {
    }
}
