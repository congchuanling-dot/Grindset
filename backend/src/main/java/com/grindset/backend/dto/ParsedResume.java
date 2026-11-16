package com.grindset.backend.dto;

import java.util.List;

/**
 * LLM 输出模型,字段与 prompt 要求的 JSON 一一对应。
 * 反序列化后还会经过规范化(枚举降级、长度截断、熟练度夹取)才会落库。
 */
public class ParsedResume {

    private String candidateName;

    /** FRESH_GRADUATE / INTERN / EXPERIENCED / UNKNOWN */
    private String candidateType;

    private List<Skill> skills;

    private List<Section> sections;

    public String getCandidateName() {
        return candidateName;
    }

    public void setCandidateName(String candidateName) {
        this.candidateName = candidateName;
    }

    public String getCandidateType() {
        return candidateType;
    }

    public void setCandidateType(String candidateType) {
        this.candidateType = candidateType;
    }

    public List<Skill> getSkills() {
        return skills;
    }

    public void setSkills(List<Skill> skills) {
        this.skills = skills;
    }

    public List<Section> getSections() {
        return sections;
    }

    public void setSections(List<Section> sections) {
        this.sections = sections;
    }

    public static class Skill {

        private String name;

        private String category;

        /** 1~5 */
        private Integer proficiency;

        private String evidence;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public Integer getProficiency() {
            return proficiency;
        }

        public void setProficiency(Integer proficiency) {
            this.proficiency = proficiency;
        }

        public String getEvidence() {
            return evidence;
        }

        public void setEvidence(String evidence) {
            this.evidence = evidence;
        }
    }

    public static class Section {

        private String type;

        private String title;

        private List<Item> items;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public List<Item> getItems() {
            return items;
        }

        public void setItems(List<Item> items) {
            this.items = items;
        }
    }

    public static class Item {

        private String title;

        private String subtitle;

        private String startDate;

        private String endDate;

        private String description;

        private List<String> tags;

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
    }
}
