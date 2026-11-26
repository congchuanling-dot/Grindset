package com.grindset.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.grindset.backend.entity.UserBalanceLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 余额流水。分页用手写 LIMIT/OFFSET,与 AiUsageRecordMapper 保持一致。
 */
@Mapper
public interface UserBalanceLogMapper extends BaseMapper<UserBalanceLog> {

    @Select("SELECT COUNT(*) FROM user_balance_log WHERE user_id = #{userId}")
    long countByUser(@Param("userId") long userId);

    @Select("""
            SELECT * FROM user_balance_log
            WHERE user_id = #{userId}
            ORDER BY created_at DESC, id DESC
            LIMIT #{size} OFFSET #{offset}
            """)
    List<UserBalanceLog> pageByUser(@Param("userId") long userId,
                                    @Param("size") int size,
                                    @Param("offset") long offset);
}
