package com.grindset.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.grindset.backend.entity.AiUsageRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 用量明细。聚合查询直接写 SQL,避免把整张明细表读进内存再统计。
 */
@Mapper
public interface AiUsageRecordMapper extends BaseMapper<AiUsageRecord> {

    @Select("""
            SELECT COUNT(*) AS calls,
                   COALESCE(SUM(input_tokens), 0) AS in_tokens,
                   COALESCE(SUM(output_tokens), 0) AS out_tokens,
                   COALESCE(SUM(cost), 0) AS cost
            FROM ai_usage_record
            WHERE user_id = #{userId} AND created_at >= #{from}
            """)
    Map<String, Object> totals(@Param("userId") long userId, @Param("from") LocalDateTime from);

    @Select("""
            SELECT biz_type AS biz_type,
                   COUNT(*) AS calls,
                   COALESCE(SUM(input_tokens), 0) AS in_tokens,
                   COALESCE(SUM(output_tokens), 0) AS out_tokens,
                   COALESCE(SUM(cost), 0) AS cost
            FROM ai_usage_record
            WHERE user_id = #{userId} AND created_at >= #{from}
            GROUP BY biz_type
            ORDER BY SUM(input_tokens) + SUM(output_tokens) DESC
            """)
    List<Map<String, Object>> byBizType(@Param("userId") long userId, @Param("from") LocalDateTime from);

    @Select("""
            SELECT provider_id AS provider_id,
                   model_id AS model_id,
                   COUNT(*) AS calls,
                   COALESCE(SUM(input_tokens), 0) AS in_tokens,
                   COALESCE(SUM(output_tokens), 0) AS out_tokens,
                   COALESCE(SUM(cost), 0) AS cost
            FROM ai_usage_record
            WHERE user_id = #{userId} AND created_at >= #{from}
            GROUP BY provider_id, model_id
            ORDER BY SUM(input_tokens) + SUM(output_tokens) DESC
            """)
    List<Map<String, Object>> byModel(@Param("userId") long userId, @Param("from") LocalDateTime from);

    @Select("""
            SELECT DATE_FORMAT(created_at, '%Y-%m-%d') AS day,
                   COALESCE(SUM(input_tokens), 0) AS in_tokens,
                   COALESCE(SUM(output_tokens), 0) AS out_tokens,
                   COALESCE(SUM(cost), 0) AS cost
            FROM ai_usage_record
            WHERE user_id = #{userId} AND created_at >= #{from}
            GROUP BY DATE_FORMAT(created_at, '%Y-%m-%d')
            ORDER BY day
            """)
    List<Map<String, Object>> daily(@Param("userId") long userId, @Param("from") LocalDateTime from);

    @Select("SELECT COUNT(*) FROM ai_usage_record WHERE user_id = #{userId}")
    long countByUser(@Param("userId") long userId);

    @Select("""
            SELECT * FROM ai_usage_record
            WHERE user_id = #{userId}
            ORDER BY created_at DESC, id DESC
            LIMIT #{size} OFFSET #{offset}
            """)
    List<AiUsageRecord> pageByUser(@Param("userId") long userId,
                                   @Param("size") int size,
                                   @Param("offset") long offset);
}
