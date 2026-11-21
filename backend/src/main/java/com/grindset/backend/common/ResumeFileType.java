package com.grindset.backend.common;

/**
 * 简历原件支持的格式。扩展名与 Content-Type 收敛在这里,
 * 避免抽取器和 OSS 存储各维护一份 switch。
 */
public enum ResumeFileType {

    PDF("pdf", "application/pdf"),
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    MD("md", "text/markdown; charset=utf-8"),
    TXT("txt", "text/plain; charset=utf-8");

    private final String extension;
    private final String contentType;

    ResumeFileType(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String extension() {
        return extension;
    }

    public String contentType() {
        return contentType;
    }

    /** 按小写扩展名匹配,不支持时返回 null */
    public static ResumeFileType fromExtension(String extension) {
        if (extension == null) {
            return null;
        }
        return switch (extension.strip().toLowerCase()) {
            case "pdf" -> PDF;
            case "docx" -> DOCX;
            case "md" -> MD;
            case "txt" -> TXT;
            default -> null;
        };
    }
}
