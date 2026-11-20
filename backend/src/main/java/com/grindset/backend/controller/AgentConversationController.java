package com.grindset.backend.controller;

import com.grindset.backend.common.BizException;
import com.grindset.backend.common.ErrorCode;
import com.grindset.backend.dto.AgentConversationDetail;
import com.grindset.backend.dto.AgentConversationSummary;
import com.grindset.backend.dto.AgentRenameRequest;
import com.grindset.backend.dto.ApiResponse;
import com.grindset.backend.security.LoginUser;
import com.grindset.backend.service.agent.AgentConversationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 首页 Agent 会话管理。
 * GET    /api/agent/conversations        会话列表
 * GET    /api/agent/conversations/{id}   会话详情(刷新/点历史时恢复)
 * PATCH  /api/agent/conversations/{id}   重命名
 * DELETE /api/agent/conversations/{id}   删除
 */
@RestController
@RequestMapping("/api/agent/conversations")
public class AgentConversationController {

    private final AgentConversationService conversationService;

    public AgentConversationController(AgentConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping
    public ApiResponse<List<AgentConversationSummary>> list(@AuthenticationPrincipal LoginUser loginUser) {
        return ApiResponse.ok(conversationService.list(userId(loginUser)));
    }

    @GetMapping("/{id}")
    public ApiResponse<AgentConversationDetail> detail(@AuthenticationPrincipal LoginUser loginUser,
                                                       @PathVariable long id) {
        return ApiResponse.ok(conversationService.detail(userId(loginUser), id));
    }

    @PatchMapping("/{id}")
    public ApiResponse<Void> rename(@AuthenticationPrincipal LoginUser loginUser,
                                    @PathVariable long id,
                                    @RequestBody AgentRenameRequest request) {
        conversationService.rename(userId(loginUser), id, request == null ? null : request.title());
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal LoginUser loginUser,
                                    @PathVariable long id) {
        conversationService.delete(userId(loginUser), id);
        return ApiResponse.ok(null);
    }

    private static long userId(LoginUser loginUser) {
        if (loginUser == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录或登录已过期");
        }
        return loginUser.userId();
    }
}
