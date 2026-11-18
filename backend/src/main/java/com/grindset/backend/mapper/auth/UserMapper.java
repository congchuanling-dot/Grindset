package com.grindset.backend.mapper.auth;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.grindset.backend.entity.auth.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 继承 BaseMapper 即白送 insert/selectById/updateById/deleteById 等单表操作,无需写 SQL。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
