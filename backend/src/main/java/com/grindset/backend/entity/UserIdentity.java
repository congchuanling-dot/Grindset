package com.grindset.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * user_identities 表对应的实体:一个用户的一种登录方式。
 */
@TableName("user_identities")
public class UserIdentity {

    @TableId(type = IdType.INPUT)
    private Long id;

    /** 关联 users.id */
    private Long userId;

    /** 登录方式:LOCAL / GITHUB / GOOGLE / EMAIL_CODE */
    private String provider;

    /** LOCAL=账号名;OAuth=对方唯一 id(sub) */
    private String identifier;

    /** 仅 LOCAL 有值,存 BCrypt 密码哈希 */
    private String credential;

    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getCredential() {
        return credential;
    }

    public void setCredential(String credential) {
        this.credential = credential;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
