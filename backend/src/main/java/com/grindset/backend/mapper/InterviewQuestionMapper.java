package com.grindset.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.grindset.backend.entity.InterviewQuestion;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface InterviewQuestionMapper extends BaseMapper<InterviewQuestion> {
}
