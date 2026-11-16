package com.grindset.backend.controller;

import com.grindset.backend.common.AdminGuard;
import com.grindset.backend.dto.ApiResponse;
import com.grindset.backend.service.WrongQuestionService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 错题历史回填接口(需要 X-Admin-Token)。
 * POST /api/admin/wrong-questions/rebuild?userId=...
 */
@RestController
@RequestMapping("/api/admin/wrong-questions")
public class AdminWrongQuestionController {

    private final AdminGuard guard;
    private final WrongQuestionService wrongQuestionService;

    public AdminWrongQuestionController(AdminGuard guard, WrongQuestionService wrongQuestionService) {
        this.guard = guard;
        this.wrongQuestionService = wrongQuestionService;
    }

    @PostMapping("/rebuild")
    public ApiResponse<WrongQuestionService.RebuildResult> rebuild(
            @RequestHeader(name = "X-Admin-Token", required = false) String token,
            @RequestParam long userId) {
        guard.check(token);
        return ApiResponse.ok(wrongQuestionService.rebuild(userId));
    }
}
