package com.grindset.backend.dto;

/**
 * 简历解析异步任务消息。只带元数据,不放文件字节。
 *
 * <p>生产者在原件上传 OSS、简历落库后投递;消费者凭 {@code resumeId} 查库,
 * 凭 {@code objectKey} 从 OSS 重新下载原件后解析。</p>
 *
 * @param resumeId  简历主键;雪花 ID 转成字符串,避免前端/跨语言解析时精度丢失
 * @param userId    简历所属用户 ID,消费者用它校验消息与库中记录归属是否一致
 * @param objectKey 原件在阿里云 OSS 私有桶中的对象 key;为空表示原件未存储,无法异步解析
 * @param fileType  文件类型枚举名(PDF/DOCX/MD/TXT),随消息透传,便于排查问题
 * @param fileName  原始文件名,消费者抽文本时按后缀分派 PDFBox / POI / 文本读取
 * @param attempt   第几次执行,1-based;首次投递为 1,每次重试递增,超过上限进死信队列
 * @param traceId   本次任务追踪 ID,用作 Redis 锁的值,也方便把同一份简历的多次重试串起来查日志
 */
public record ResumeParseMessage(
        String resumeId,
        String userId,
        String objectKey,
        String fileType,
        String fileName,
        int attempt,
        String traceId) {
}
