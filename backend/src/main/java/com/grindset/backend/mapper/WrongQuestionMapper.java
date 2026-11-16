package com.grindset.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.grindset.backend.entity.WrongQuestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface WrongQuestionMapper extends BaseMapper<WrongQuestion> {

    /** 行锁:同一错题并发重练时串行更新重练次数和状态 */
    @Select("SELECT * FROM wrong_questions WHERE id = #{id} FOR UPDATE")
    WrongQuestion selectForUpdate(@Param("id") long id);
}
