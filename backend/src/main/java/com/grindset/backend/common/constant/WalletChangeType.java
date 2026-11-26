package com.grindset.backend.common.constant;

/**
 * 余额变动类型。数据库存 {@link #name()},前端展示 {@link #label()}。
 */
public enum WalletChangeType {

    INIT("初始额度"),
    RECHARGE("充值"),
    CONSUME("AI 消费"),
    ADJUST("调账");

    private final String label;

    WalletChangeType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 历史数据出现未知类型时按原名展示,不让接口打挂 */
    public static String labelOf(String name) {
        if (name == null || name.isBlank()) {
            return "其他";
        }
        for (WalletChangeType type : values()) {
            if (type.name().equals(name)) {
                return type.label;
            }
        }
        return name;
    }
}
