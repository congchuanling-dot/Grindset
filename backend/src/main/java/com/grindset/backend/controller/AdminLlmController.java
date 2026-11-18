package com.grindset.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.grindset.backend.common.AdminGuard;
import com.grindset.backend.common.BizException;
import com.grindset.backend.common.ErrorCode;
import com.grindset.backend.dto.ApiResponse;
import com.grindset.backend.entity.LlmChannel;
import com.grindset.backend.entity.LlmKey;
import com.grindset.backend.mapper.LlmChannelMapper;
import com.grindset.backend.mapper.LlmKeyMapper;
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

    private final AdminGuard guard;
    private final LlmChannelMapper channelMapper;
    private final LlmKeyMapper keyMapper;
    private final LlmKeyService keyService;
    private final BalanceService balanceService;

    public AdminLlmController(AdminGuard guard, LlmChannelMapper channelMapper, LlmKeyMapper keyMapper,
                              LlmKeyService keyService, BalanceService balanceService) {
        this.guard = guard;
        this.channelMapper = channelMapper;
        this.keyMapper = keyMapper;
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
    @PostMapping("/channels/{id}/balance/refresh")
    public ApiResponse<Map<String, Object>> refreshBalance(
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
}
