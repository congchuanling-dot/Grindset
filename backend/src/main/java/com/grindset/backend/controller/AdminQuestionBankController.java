package com.grindset.backend.controller;

import com.grindset.backend.common.AdminGuard;
import com.grindset.backend.dto.ApiResponse;
import com.grindset.backend.dto.QuestionBankImportRequest;
import com.grindset.backend.dto.QuestionBankImportResult;
import com.grindset.backend.dto.QuestionImageUploadResult;
import com.grindset.backend.service.QuestionBankService;
import com.grindset.backend.service.QuestionImageService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 题库导入接口（需要 X-Admin-Token）。
 * POST /api/admin/questions/import  JSON 批量导入，按题干内容哈希幂等。
 * POST /api/admin/questions/images  上传配图到 OSS，返回后端代理 URL。
 */
@RestController
@RequestMapping("/api/admin/questions")
public class AdminQuestionBankController {

    private final AdminGuard guard;
    private final QuestionBankService questionBankService;
    private final QuestionImageService questionImageService;

    public AdminQuestionBankController(AdminGuard guard, QuestionBankService questionBankService,
                                       QuestionImageService questionImageService) {
        this.guard = guard;
        this.questionBankService = questionBankService;
        this.questionImageService = questionImageService;
    }

    @PostMapping("/import")
    public ApiResponse<QuestionBankImportResult> importQuestions(
            @RequestHeader(name = "X-Admin-Token", required = false) String token,
            @RequestBody QuestionBankImportRequest request) {
        guard.check(token);
        return ApiResponse.ok(questionBankService.importQuestions(
                request == null ? null : request.questions()));
    }

    @PostMapping("/images")
    public ApiResponse<QuestionImageUploadResult> uploadImage(
            @RequestHeader(name = "X-Admin-Token", required = false) String token,
            @RequestParam("file") MultipartFile file) {
        guard.check(token);
        return ApiResponse.ok(questionImageService.upload(file));
    }
}
