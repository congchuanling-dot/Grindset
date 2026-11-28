package com.grindset.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 弱项训练配置。所有阈值集中在这里，业务代码不写死数字。
 */
@Component
@ConfigurationProperties(prefix = "app.training")
public class TrainingProperties {

    private boolean enabled = true;

    /** 每个计划的题目数 */
    private int planItems = 5;

    /** 单场面试最多取几个弱项进入训练 */
    private int maxWeaknesses = 3;

    /** 计划有效期（天） */
    private int planExpireDays = 7;

    /** 同一用户同时进行中的计划上限 */
    private int maxActivePlans = 3;

    /** 单计划最多 AI 生成题数；只发一次生成请求 */
    private int maxAiItems = 5;

    /** 单用户每日生成计划上限，防刷 */
    private int dailyPlanLimit = 10;

    private Mastery mastery = new Mastery();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getPlanItems() {
        return planItems;
    }

    public void setPlanItems(int planItems) {
        this.planItems = planItems;
    }

    public int getMaxWeaknesses() {
        return maxWeaknesses;
    }

    public void setMaxWeaknesses(int maxWeaknesses) {
        this.maxWeaknesses = maxWeaknesses;
    }

    public int getPlanExpireDays() {
        return planExpireDays;
    }

    public void setPlanExpireDays(int planExpireDays) {
        this.planExpireDays = planExpireDays;
    }

    public int getMaxActivePlans() {
        return maxActivePlans;
    }

    public void setMaxActivePlans(int maxActivePlans) {
        this.maxActivePlans = maxActivePlans;
    }

    public int getMaxAiItems() {
        return maxAiItems;
    }

    public void setMaxAiItems(int maxAiItems) {
        this.maxAiItems = maxAiItems;
    }

    public int getDailyPlanLimit() {
        return dailyPlanLimit;
    }

    public void setDailyPlanLimit(int dailyPlanLimit) {
        this.dailyPlanLimit = dailyPlanLimit;
    }

    public Mastery getMastery() {
        return mastery;
    }

    public void setMastery(Mastery mastery) {
        this.mastery = mastery;
    }

    public static class Mastery {

        /** 初始掌握度 */
        private int initial = 50;

        /** 普通观测权重 */
        private double emaAlpha = 0.3;

        /** 复测观测权重 */
        private double retestAlpha = 0.5;

        public int getInitial() {
            return initial;
        }

        public void setInitial(int initial) {
            this.initial = initial;
        }

        public double getEmaAlpha() {
            return emaAlpha;
        }

        public void setEmaAlpha(double emaAlpha) {
            this.emaAlpha = emaAlpha;
        }

        public double getRetestAlpha() {
            return retestAlpha;
        }

        public void setRetestAlpha(double retestAlpha) {
            this.retestAlpha = retestAlpha;
        }
    }
}
