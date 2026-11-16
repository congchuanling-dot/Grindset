package com.grindset.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * knowledge_points 表:全局知识点字典。
 * normalized_name 唯一,用来合并"Redis 持久化 / redis持久化"这类写法差异。
 */
@TableName("knowledge_points")
public class KnowledgePoint {

    @TableId(type = IdType.INPUT)
    private Long id;

    private String name;

    private String normalizedName;

    /** Redis / MySQL / JVM / Java / 网络 / 操作系统 / 算法 / AI / 项目 / 其他 */
    private String domain;

    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public void setNormalizedName(String normalizedName) {
        this.normalizedName = normalizedName;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
