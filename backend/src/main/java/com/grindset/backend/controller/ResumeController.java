package com.grindset.backend.controller;

import com.grindset.backend.common.BizException;
import com.grindset.backend.common.ErrorCode;
import com.grindset.backend.dto.ApiResponse;
import com.grindset.backend.dto.ResumeDetail;
import com.grindset.backend.dto.ResumeSummary;
import com.grindset.backend.security.LoginUser;
import com.grindset.backend.service.ResumeOriginalStorage;
import com.grindset.backend.service.ResumeService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
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

    /**
     * 代理返回原件,用于 PDF 内嵌预览。
     * OSS 默认域名会强制下载,所以不能把签名 URL 直接交给 iframe。
     */
    @GetMapping("/{id}/original")
    public void original(@AuthenticationPrincipal LoginUser loginUser,
                         @PathVariable long id,
                         HttpServletResponse response) throws IOException {
        ResumeService.OriginalFile file = resumeService.openOriginal(userId(loginUser), id);
        response.setContentType(ResumeOriginalStorage.contentTypeOf(file.fileType()));
        if (file.size() > 0) {
            response.setContentLengthLong(file.size());
        }
        response.setHeader("Content-Disposition", ResumeOriginalStorage.inlineDisposition(file.fileName()));
        response.setHeader("Cache-Control", "private, max-age=300");
        try (InputStream in = file.stream()) {
            in.transferTo(response.getOutputStream());
        }
    }

    private static long userId(LoginUser loginUser) {
        if (loginUser == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "未登录或登录已过期");
        }
        return loginUser.userId();
    }
}
