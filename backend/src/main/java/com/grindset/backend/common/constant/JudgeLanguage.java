package com.grindset.backend.common.constant;

/**
 * 判题支持的编程语言。值是提交接口与判题镜像约定的标识。
 */
public enum JudgeLanguage {

    /** Java,源码文件固定为 Main.java,先编译再逐用例运行 */
    JAVA,

    /** Python,源码文件固定为 main.py,直接解释执行 */
    PYTHON;

    /**
     * 兼容前端/历史数据的别名,如 py、python3、jdk;不认识返回 null。
     */
    public static JudgeLanguage from(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return switch (raw.strip().toUpperCase()) {
            case "JAVA", "JDK" -> JAVA;
            case "PYTHON", "PY", "PYTHON3" -> PYTHON;
            default -> null;
        };
    }
}
