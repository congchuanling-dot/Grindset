package com.grindset.backend.controller;

import com.grindset.backend.common.AdminGuard;
import com.grindset.backend.dto.ApiResponse;
import com.grindset.backend.service.KnowledgeProfileService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 画像维护接口(需要 X-Admin-Token),用于历史回填和失败修复。
 * POST /api/admin/knowledge/rebuild?userId=...
 */
@RestController
@RequestMapping("/api/admin/knowledge")
public class AdminKnowledgeController {

    private final AdminGuard guard;
    private final KnowledgeProfileService profileService;

    public AdminKnowledgeController(AdminGuard guard, KnowledgeProfileService profileService) {
        this.guard = guard;
        this.profileService = profileService;
    }

    @PostMapping("/rebuild")
    public ApiResponse<KnowledgeProfileService.RebuildResult> rebuild(
            @RequestHeader(name = "X-Admin-Token", required = false) String token,
            @RequestParam long userId) {
        guard.check(token);
        return ApiResponse.ok(profileService.rebuild(userId));
    }
}
