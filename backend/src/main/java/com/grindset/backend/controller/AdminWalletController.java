package com.grindset.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.grindset.backend.common.AdminGuard;
import com.grindset.backend.common.BizException;
import com.grindset.backend.common.ErrorCode;
import com.grindset.backend.dto.ApiResponse;
import com.grindset.backend.dto.WalletDtos;
import com.grindset.backend.entity.auth.User;
import com.grindset.backend.entity.auth.UserIdentity;
import com.grindset.backend.mapper.auth.UserIdentityMapper;
import com.grindset.backend.mapper.auth.UserMapper;
import com.grindset.backend.service.WalletService;
import com.grindset.backend.service.auth.IdentityService;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

/**
 * 用户余额管理接口(需要 X-Admin-Token)。
 * 按邮箱定位账号(邮箱唯一),直接设置为指定余额并记一条调账流水。
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminWalletController {

    private final AdminGuard guard;
    private final UserMapper userMapper;
    private final UserIdentityMapper identityMapper;
    private final WalletService walletService;

    public AdminWalletController(AdminGuard guard, UserMapper userMapper,
                                 UserIdentityMapper identityMapper, WalletService walletService) {
        this.guard = guard;
        this.userMapper = userMapper;
        this.identityMapper = identityMapper;
        this.walletService = walletService;
    }

    /** 按邮箱修改账号余额,余额会被直接覆盖为入参值 */
    @PutMapping("/balance")
    public ApiResponse<BalanceView> adjustBalance(
            @RequestHeader(name = "X-Admin-Token", required = false) String token,
            @RequestBody AdjustBalanceRequest req) {
        guard.check(token);

        String email = req == null || req.email() == null ? "" : req.email().strip();
        if (email.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "邮箱不能为空");
        }
        if (req.balance() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "余额不能为空");
        }
        if (req.balance().signum() < 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "余额不能为负数");
        }

        User user = resolveByEmail(email);
        if (user == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "没有找到该邮箱对应的账号");
        }

        BigDecimal previous = walletService.getInfo(user.getId()).balance();
        WalletDtos.Info info = walletService.adjustBalance(user.getId(), req.balance());
        String displayEmail = user.getEmail() == null || user.getEmail().isBlank() ? email : user.getEmail();
        return ApiResponse.ok(new BalanceView(String.valueOf(user.getId()), displayEmail,
                user.getNickname(), previous, info.balance(), info.currency(), info.updatedAt()));
    }

    /**
     * 按邮箱定位账号。邮箱不一定存在 users.email 上(老数据的本地账号 users.email 可能为空),
     * 所以先按 LOCAL 登录标识查 user_identities(有唯一索引),再回退 users.email。
     * users.email 没有唯一索引,可能对应多个 OAuth 账号,回退时取 id 最小的一个(与历史行为一致)。
     */
    private User resolveByEmail(String email) {
        UserIdentity local = identityMapper.selectOne(new QueryWrapper<UserIdentity>()
                .eq("provider", IdentityService.PROVIDER_LOCAL)
                .eq("identifier", email)
                .last("LIMIT 1"));
        if (local != null) {
            User user = userMapper.selectById(local.getUserId());
            if (user != null) {
                return user;
            }
        }
        return userMapper.selectOne(new QueryWrapper<User>()
                .eq("email", email)
                .orderByAsc("id")
                .last("LIMIT 1"));
    }

    public record AdjustBalanceRequest(String email, BigDecimal balance) {
    }

    public record BalanceView(String userId, String email, String nickname,
                              BigDecimal previousBalance, BigDecimal balance,
                              String currency, String updatedAt) {
    }
}
