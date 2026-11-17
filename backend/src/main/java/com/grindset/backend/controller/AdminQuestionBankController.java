package com.grindset.backend.controller;

import com.grindset.backend.common.AdminGuard;
import com.grindset.backend.dto.ApiResponse;
import com.grindset.backend.dto.QuestionBankImportRequest;
import com.grindset.backend.dto.QuestionBankImportResult;
import com.grindset.backend.service.QuestionBankService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 题库导入接口（需要 X-Admin-Token）。
 * POST /api/admin/questions/import  JSON 批量导入，按题干内容哈希幂等。
 */
@RestController
@RequestMapping("/api/admin/questions")
public class AdminQuestionBankController {

    private final AdminGuard guard;
    private final QuestionBankService questionBankService;

    public AdminQuestionBankController(AdminGuard guard, QuestionBankService questionBankService) {
        this.guard = guard;
        this.questionBankService = questionBankService;
    }

    @PostMapping("/import")
    public ApiResponse<QuestionBankImportResult> importQuestions(
            @RequestHeader(name = "X-Admin-Token", required = false) String token,
            @RequestBody QuestionBankImportRequest request) {
        guard.check(token);
        return ApiResponse.ok(questionBankService.importQuestions(
                request == null ? null : request.questions()));
    }
}
