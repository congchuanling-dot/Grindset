package com.grindset.backend.service.agent;

/**
 * 客户端主动断开(点停止、关页面、切换会话)时抛出。
 *
 * <p>和业务错误分开:它不是失败,已经生成的内容仍然要落库,
 * 所以 AgentService 会捕获它、带着已有内容正常返回。</p>
 */
public class AgentStreamAbortedException extends RuntimeException {
}
