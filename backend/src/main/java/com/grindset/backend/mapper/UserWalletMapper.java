package com.grindset.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.grindset.backend.entity.UserWallet;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * 钱包。扣费用原子 UPDATE,WHERE balance >= amount 保证余额不会被扣成负数。
 */
@Mapper
public interface UserWalletMapper extends BaseMapper<UserWallet> {

    /**
     * 原子扣减。返回 0 表示余额不足(或钱包不存在),调用方按"这笔超支"记日志。
     */
    @Update("""
            UPDATE user_wallets
            SET balance = balance - #{amount}
            WHERE user_id = #{userId} AND balance >= #{amount}
            """)
    int deduct(@Param("userId") long userId, @Param("amount") BigDecimal amount);
}
