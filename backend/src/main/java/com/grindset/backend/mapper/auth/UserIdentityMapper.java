package com.grindset.backend.mapper.auth;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.grindset.backend.entity.auth.UserIdentity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserIdentityMapper extends BaseMapper<UserIdentity> {
}
