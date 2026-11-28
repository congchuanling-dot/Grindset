package com.grindset.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.grindset.backend.common.AiBizType;
import com.grindset.backend.common.BizException;
import com.grindset.backend.common.ErrorCode;
import com.grindset.backend.common.SnowflakeIdGenerator;
import com.grindset.backend.common.constant.TimeFormats;
import com.grindset.backend.common.constant.WalletChangeType;
import com.grindset.backend.dto.WalletDtos;
import com.grindset.backend.entity.UserBalanceLog;
import com.grindset.backend.entity.UserWallet;
import com.grindset.backend.mapper.UserBalanceLogMapper;
import com.grindset.backend.mapper.UserWalletMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 用户钱包:余额查询、流水、AI 消费扣减。
 *
 * <p>本期不做幂等,靠原子 UPDATE 的 {@code balance >= amount} 保证余额不会被扣成负数;
 * 余额不足时最多透支一次调用。存量用户首次访问时懒创建钱包,不需要补数脚本。</p>
 */
@Service
public class WalletService {

    private static final Logger log = LoggerFactory.getLogger(WalletService.class);

    /** 新注册用户赠送 1 元 */
    private static final BigDecimal DEFAULT_BALANCE = new BigDecimal("1.00000000");
    private static final String DEFAULT_CURRENCY = "CNY";
    private static final int MAX_PAGE_SIZE = 100;

    private final UserWalletMapper walletMapper;
    private final UserBalanceLogMapper logMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final TransactionTemplate transactionTemplate;

    public WalletService(UserWalletMapper walletMapper, UserBalanceLogMapper logMapper,
                         SnowflakeIdGenerator idGenerator, TransactionTemplate transactionTemplate) {
        this.walletMapper = walletMapper;
        this.logMapper = logMapper;
        this.idGenerator = idGenerator;
        this.transactionTemplate = transactionTemplate;
    }

    /** 注册时调用,在注册事务内创建钱包 + 初始流水 */
    public void initWallet(long userId) {
        ensureWallet(userId);
    }

    /**
     * 取钱包,没有就懒创建(默认 1 元 + INIT 流水)。
     * 唯一索引兜底并发:两个线程同时建,后到的捕获重复键后重查。
     */
    public UserWallet ensureWallet(long userId) {
        UserWallet wallet = findByUser(userId);
        if (wallet != null) {
            return wallet;
        }
        try {
            return transactionTemplate.execute(status -> createWallet(userId));
        } catch (DuplicateKeyException e) {
            return findByUser(userId);
        }
    }

    public WalletDtos.Info getInfo(long userId) {
        UserWallet wallet = ensureWallet(userId);
        LocalDateTime updatedAt = wallet.getUpdatedAt() != null
                ? wallet.getUpdatedAt()
                : wallet.getCreatedAt();
        return new WalletDtos.Info(nz(wallet.getBalance()), wallet.getCurrency(),
                updatedAt == null ? "" : TimeFormats.STANDARD.format(updatedAt));
    }

    public WalletDtos.LogPage logs(long userId, int page, int size) {
        int current = Math.max(page, 1);
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        long total = logMapper.countByUser(userId);
        List<UserBalanceLog> rows = total == 0
                ? List.of()
                : logMapper.pageByUser(userId, pageSize, (long) (current - 1) * pageSize);

        List<WalletDtos.LogView> items = new ArrayList<>(rows.size());
        for (UserBalanceLog row : rows) {
            String bizType = row.getBizType();
            items.add(new WalletDtos.LogView(String.valueOf(row.getId()),
                    nz(row.getChangeAmount()), nz(row.getBalanceAfter()),
                    row.getChangeType(), WalletChangeType.labelOf(row.getChangeType()),
                    bizType, bizType == null ? "" : AiBizType.labelOf(bizType), row.getBizRef(),
                    row.getRemark(),
                    row.getCreatedAt() == null ? "" : TimeFormats.STANDARD.format(row.getCreatedAt())));
        }
        return new WalletDtos.LogPage(items, total, current, pageSize);
    }

    /**
     * 模型调用前的余额闸门。余额 > 0 才放行。
     * 数据库异常时 fail-open(记账链路本来就是容错的),避免钱包把 AI 主流程拖垮。
     */
    public void assertAffordable(long userId) {
        try {
            UserWallet wallet = ensureWallet(userId);
            BigDecimal balance = wallet.getBalance();
            if (balance == null || balance.signum() <= 0) {
                throw new BizException(ErrorCode.PAYMENT_REQUIRED, "余额不足，请充值后继续");
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.warn("余额校验失败,本次调用放行 userId={}", userId, e);
        }
    }

    /**
     * 消费扣减。由 AiUsageService 在记账成功后调用。
     * 金额 <= 0 直接跳过(模型没配价时成本为 0,不产生 0 元流水)。
     * 事务只覆盖"扣余额 + 写流水"两件事,异常由调用方(记账链路)吞掉。
     */
    public void consume(long userId, BigDecimal amount, String bizType, String bizRef) {
        if (amount == null || amount.signum() <= 0) {
            return;
        }
        transactionTemplate.executeWithoutResult(status -> {
            UserWallet wallet = findByUser(userId);
            BigDecimal before = wallet == null ? BigDecimal.ZERO : nz(wallet.getBalance());
            int rows = walletMapper.deduct(userId, amount);
            if (rows == 0) {
                // 余额在"校验"和"扣费"之间被并发花掉;本条按超支处理
                log.warn("余额不足,本次消费未扣减 userId={} amount={} bizType={} bizRef={}",
                        userId, amount, bizType, bizRef);
                return;
            }
            insertLog(userId, amount.negate(), before, before.subtract(amount),
                    WalletChangeType.CONSUME.name(), bizType, bizRef, null);
        });
    }

    /**
     * 管理后台直接设置余额。按目标余额覆盖,差额记一条 ADJUST 流水,便于对账。
     * 余额不变时不写流水,只更新更新时间。
     */
    public WalletDtos.Info adjustBalance(long userId, BigDecimal target) {
        return transactionTemplate.execute(status -> {
            UserWallet wallet = ensureWallet(userId);
            BigDecimal before = nz(wallet.getBalance());
            UserWallet patch = new UserWallet();
            patch.setId(wallet.getId());
            patch.setBalance(target);
            walletMapper.updateById(patch);

            BigDecimal delta = target.subtract(before);
            if (delta.signum() != 0) {
                insertLog(userId, delta, before, target, WalletChangeType.ADJUST.name(),
                        null, null, "管理员调整余额");
            }
            return new WalletDtos.Info(target, wallet.getCurrency(),
                    TimeFormats.STANDARD.format(LocalDateTime.now()));
        });
    }

    private UserWallet createWallet(long userId) {
        UserWallet wallet = new UserWallet();
        wallet.setId(idGenerator.nextId());
        wallet.setUserId(userId);
        wallet.setBalance(DEFAULT_BALANCE);
        wallet.setCurrency(DEFAULT_CURRENCY);
        walletMapper.insert(wallet);
        insertLog(userId, DEFAULT_BALANCE, BigDecimal.ZERO, DEFAULT_BALANCE,
                WalletChangeType.INIT.name(), null, null, null);
        return wallet;
    }

    private void insertLog(long userId, BigDecimal changeAmount, BigDecimal before, BigDecimal after,
                           String changeType, String bizType, String bizRef, String remark) {
        UserBalanceLog entry = new UserBalanceLog();
        entry.setId(idGenerator.nextId());
        entry.setUserId(userId);
        entry.setChangeAmount(changeAmount);
        entry.setBalanceBefore(before);
        entry.setBalanceAfter(after);
        entry.setChangeType(changeType);
        entry.setBizType(bizType);
        entry.setBizRef(bizRef);
        entry.setRemark(remark);
        logMapper.insert(entry);
    }

    private UserWallet findByUser(long userId) {
        return walletMapper.selectOne(new QueryWrapper<UserWallet>()
                .eq("user_id", userId)
                .last("LIMIT 1"));
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
