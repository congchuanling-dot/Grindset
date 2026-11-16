package com.grindset.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.grindset.backend.entity.UserKnowledgeState;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserKnowledgeStateMapper extends BaseMapper<UserKnowledgeState> {

    /** 行锁:并发回答同一知识点时串行更新,避免读改写丢更新 */
    @Select("SELECT * FROM user_knowledge_states WHERE user_id = #{userId} AND kp_id = #{kpId} FOR UPDATE")
    UserKnowledgeState selectForUpdate(@Param("userId") long userId, @Param("kpId") long kpId);
}
