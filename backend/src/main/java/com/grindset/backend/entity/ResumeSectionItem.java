package com.grindset.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

import java.util.List;

/**
 * resume_section_items 表:章节下的一条经历。
 * tags 是 JSON 列,由 JacksonTypeHandler 直接映射为 List<String>。
 */
@TableName(value = "resume_section_items", autoResultMap = true)
public class ResumeSectionItem {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long sectionId;

    /** 公司 / 学校 / 项目 / 论文 / 奖项名 */
    private String title;

    /** 角色 / 专业 / 级别 */
    private String subtitle;

    private String startDate;

    private String endDate;

    private String description;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;

    private Integer sort;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSectionId() {
        return sectionId;
    }

    public void setSectionId(Long sectionId) {
        this.sectionId = sectionId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
    }
}
