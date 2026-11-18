package com.grindset.backend.controller;

import com.grindset.backend.common.BizException;
import com.grindset.backend.common.ErrorCode;
import com.grindset.backend.dto.ApiResponse;
import com.grindset.backend.dto.KnowledgeProfileView;
import com.grindset.backend.security.LoginUser;
import com.grindset.backend.service.KnowledgeProfileService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 掌握度画像接口。
 * GET /api/knowledge/profile?level=WEAK&domain=Redis&limit=100
 */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private final KnowledgeProfileService profileService;

    public KnowledgeController(KnowledgeProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/profile")
    public ApiResponse<KnowledgeProfileView> profile(@AuthenticationPrincipal LoginUser loginUser,
                                                     @RequestParam(required = false) String level,
                                                     @RequestParam(required = false) String domain,
                                                     @RequestParam(defaultValue = "100") int limit) {
        long userId = userId(loginUser);
        return ApiResponse.ok(profileService.profile(userId, level, domain, limit));
    }

    private static long userId(LoginUser loginUser) {
        if (loginUser == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录或登录已过期");
        }
        return loginUser.userId();
    }
}
