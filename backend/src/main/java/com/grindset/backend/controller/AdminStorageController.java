package com.grindset.backend.controller;

import com.grindset.backend.common.AdminGuard;
import com.grindset.backend.dto.ApiResponse;
import com.grindset.backend.dto.StorageConfigUpdate;
import com.grindset.backend.dto.StorageConfigView;
import com.grindset.backend.service.StorageConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 存储配置管理接口(需要 X-Admin-Token)。
 * 目前只有阿里云 OSS:凭证密文存库,接口只返回 AccessKey 掩码。
 */
@RestController
@RequestMapping("/api/admin/storage")
public class AdminStorageController {

    private final AdminGuard guard;
    private final StorageConfigService storageConfigService;

    public AdminStorageController(AdminGuard guard, StorageConfigService storageConfigService) {
        this.guard = guard;
        this.storageConfigService = storageConfigService;
    }

    @GetMapping("/oss")
    public ApiResponse<StorageConfigView> get(
            @RequestHeader(name = "X-Admin-Token", required = false) String token) {
        guard.check(token);
        return ApiResponse.ok(storageConfigService.view());
    }

    @PutMapping("/oss")
    public ApiResponse<StorageConfigView> update(
            @RequestHeader(name = "X-Admin-Token", required = false) String token,
            @RequestBody StorageConfigUpdate req) {
        guard.check(token);
        return ApiResponse.ok(storageConfigService.update(req));
    }
}
