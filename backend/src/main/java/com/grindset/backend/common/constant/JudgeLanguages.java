package com.grindset.backend.common.constant;

/**
 * 判题支持的编程语言。值是提交接口与判题镜像约定的标识。
 */
public final class JudgeLanguages {

    /** Java,源码文件固定为 Main.java,先编译再逐用例运行 */
    public static final String JAVA = "JAVA";

    /** Python,源码文件固定为 main.py,直接解释执行 */
    public static final String PYTHON = "PYTHON";

    private JudgeLanguages() {
    }
}
