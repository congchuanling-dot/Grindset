package com.grindset.backend.controller;

import com.grindset.backend.common.BizException;
import com.grindset.backend.dto.ApiResponse;
import com.grindset.backend.dto.ResumeDetail;
import com.grindset.backend.dto.ResumeSummary;
import com.grindset.backend.security.LoginUser;
import com.grindset.backend.service.ResumeService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 简历接口。
 * POST   /api/resumes       上传并同步解析
 * GET    /api/resumes       当前用户简历列表
 * GET    /api/resumes/{id}  简历详情
 * DELETE /api/resumes/{id}  删除简历及子表数据
 */
@RestController
@RequestMapping("/api/resumes")
public class ResumeController {

    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    @PostMapping
    public ApiResponse<ResumeDetail> upload(@AuthenticationPrincipal LoginUser loginUser,
                                            @RequestParam(name = "file", required = false) MultipartFile file) {
        return ApiResponse.ok(resumeService.upload(userId(loginUser), file));
    }

    @GetMapping
    public ApiResponse<List<ResumeSummary>> list(@AuthenticationPrincipal LoginUser loginUser) {
        return ApiResponse.ok(resumeService.list(userId(loginUser)));
    }

    @GetMapping("/{id}")
    public ApiResponse<ResumeDetail> detail(@AuthenticationPrincipal LoginUser loginUser,
                                            @PathVariable long id) {
        return ApiResponse.ok(resumeService.get(userId(loginUser), id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@AuthenticationPrincipal LoginUser loginUser,
                                    @PathVariable long id) {
        resumeService.delete(userId(loginUser), id);
        return ApiResponse.ok(null);
    }

    private static long userId(LoginUser loginUser) {
        if (loginUser == null) {
            throw new BizException(401, "未登录或登录已过期");
        }
        return loginUser.userId();
    }
}
