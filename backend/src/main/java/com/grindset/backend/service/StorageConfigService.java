package com.grindset.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.grindset.backend.common.BizException;
import com.grindset.backend.common.ErrorCode;
import com.grindset.backend.common.SnowflakeIdGenerator;
import com.grindset.backend.config.ResumeProperties;
import com.grindset.backend.dto.StorageConfigUpdate;
import com.grindset.backend.dto.StorageConfigView;
import com.grindset.backend.entity.StorageConfig;
import com.grindset.backend.llm.SecretCipher;
import com.grindset.backend.mapper.StorageConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import static com.grindset.backend.common.constant.TimeFormats.STANDARD;

/**
 * 外部存储配置:数据库密文优先,环境变量兜底。
 * 每次调用都直接查库,管理接口改完立即生效,不做进程内缓存。
 */
@Service
public class StorageConfigService {

    private static final Logger log = LoggerFactory.getLogger(StorageConfigService.class);

    public static final String PROVIDER_ALIYUN_OSS = "ALIYUN_OSS";

    private static final int MIN_EXPIRE_SECONDS = 60;
    private static final int MAX_EXPIRE_SECONDS = 24 * 3600;
    private static final String DEFAULT_DIR_PREFIX = "resumes";

    private final StorageConfigMapper mapper;
    private final SecretCipher cipher;
    private final SnowflakeIdGenerator idGenerator;
    private final ResumeProperties properties;

    /** 表还没建时只提示一次,避免每次上传都刷日志 */
    private volatile boolean schemaWarned;

    public StorageConfigService(StorageConfigMapper mapper, SecretCipher cipher,
                                SnowflakeIdGenerator idGenerator, ResumeProperties properties) {
        this.mapper = mapper;
        this.cipher = cipher;
        this.idGenerator = idGenerator;
        this.properties = properties;
    }

    /**
     * 解析当前生效的 OSS 配置;未配置、被停用或密文解不开都返回 null,
     * 调用方按"跳过原件存储"处理,不能影响上传解析主流程。
     */
    public ResolvedOss resolve() {
        StorageConfig row;
        try {
            row = findRow();
        } catch (RuntimeException e) {
            if (!schemaWarned) {
                schemaWarned = true;
                log.warn("查询 storage_config 失败,原件存储按未配置处理: {}", e.getMessage());
            }
            return envResolved();
        }
        if (row != null) {
            if (row.getEnabled() == null || row.getEnabled() != 1) {
                return null;
            }
            if (!complete(row.getEndpoint(), row.getBucket())) {
                log.warn("storage_config 已启用但 endpoint/bucket 为空,跳过原件存储");
                return null;
            }
            try {
                String accessKeyId = cipher.decrypt(row.getAccessKeyIdCipher(), row.getId());
                String accessKeySecret = cipher.decrypt(row.getAccessKeySecretCipher(), row.getId());
                if (!complete(accessKeyId, accessKeySecret)) {
                    log.warn("storage_config 的 AccessKey 为空,跳过原件存储");
                    return null;
                }
                return new ResolvedOss(row.getEndpoint().strip(), row.getBucket().strip(),
                        dirPrefix(row.getDirPrefix()), expire(row.getUrlExpireSeconds()),
                        accessKeyId, accessKeySecret);
            } catch (RuntimeException e) {
                log.warn("读取 storage_config 失败,可能是 SECRET_MASTER_KEY 被更换,跳过原件存储: {}", e.getMessage());
                return null;
            }
        }
        return envResolved();
    }

    /** 管理接口展示:只返回 AccessKey 掩码 */
    public StorageConfigView view() {
        StorageConfig row = findRow();
        if (row != null) {
            String mask = "****";
            try {
                mask = SecretCipher.mask(cipher.decrypt(row.getAccessKeyIdCipher(), row.getId()));
            } catch (RuntimeException e) {
                log.warn("展示 storage_config 时解密失败,请检查 SECRET_MASTER_KEY");
            }
            return new StorageConfigView(String.valueOf(row.getId()), row.getProvider(),
                    row.getEndpoint(), row.getBucket(), dirPrefix(row.getDirPrefix()),
                    expire(row.getUrlExpireSeconds()),
                    row.getEnabled() != null && row.getEnabled() == 1,
                    mask, row.getAccessKeySecretCipher() != null && !row.getAccessKeySecretCipher().isBlank(),
                    "DB", row.getUpdatedAt() == null ? null : row.getUpdatedAt().format(STANDARD));
        }

        ResumeProperties.Oss env = properties.getOss();
        boolean envComplete = complete(env.getEndpoint(), env.getBucket())
                && complete(env.getAccessKeyId(), env.getAccessKeySecret());
        return new StorageConfigView(null, PROVIDER_ALIYUN_OSS, blankToNull(env.getEndpoint()),
                blankToNull(env.getBucket()), dirPrefix(env.getDirPrefix()), expire(env.getUrlExpireSeconds()),
                envComplete, envComplete ? SecretCipher.mask(env.getAccessKeyId()) : null,
                !isBlank(env.getAccessKeySecret()), envComplete ? "ENV" : "NONE", null);
    }

    /** 新增或更新配置;AccessKey 不传时保留原密文,其他未传字段沿用已有值 */
    public StorageConfigView update(StorageConfigUpdate req) {
        if (req == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请求体不能为空");
        }
        String endpoint = required(req.endpoint(), "endpoint");
        String bucket = required(req.bucket(), "bucket");

        boolean hasAccessKeyId = !isBlank(req.accessKeyId());
        boolean hasAccessKeySecret = !isBlank(req.accessKeySecret());
        if (hasAccessKeyId != hasAccessKeySecret) {
            throw new BizException(ErrorCode.BAD_REQUEST, "AccessKeyId 和 AccessKeySecret 必须同时提供");
        }

        StorageConfig row = findRow();
        int expire = req.urlExpireSeconds() == null
                ? (row == null ? 3600 : expire(row.getUrlExpireSeconds()))
                : expire(req.urlExpireSeconds());
        String dirPrefix = req.dirPrefix() == null
                ? (row == null ? DEFAULT_DIR_PREFIX : dirPrefix(row.getDirPrefix()))
                : dirPrefix(req.dirPrefix());
        boolean enabled = req.enabled() != null
                ? req.enabled()
                : row == null || row.getEnabled() == null || row.getEnabled() == 1;

        if (row == null) {
            if (!hasAccessKeyId) {
                throw new BizException(ErrorCode.BAD_REQUEST, "首次配置必须提供 AccessKeyId 和 AccessKeySecret");
            }
            StorageConfig created = new StorageConfig();
            created.setId(idGenerator.nextId());
            created.setProvider(PROVIDER_ALIYUN_OSS);
            created.setEndpoint(endpoint);
            created.setBucket(bucket);
            created.setDirPrefix(dirPrefix);
            created.setUrlExpireSeconds(expire);
            created.setEnabled(enabled ? 1 : 0);
            created.setAccessKeyIdCipher(cipher.encrypt(req.accessKeyId().strip(), created.getId()));
            created.setAccessKeySecretCipher(cipher.encrypt(req.accessKeySecret().strip(), created.getId()));
            mapper.insert(created);
        } else {
            row.setEndpoint(endpoint);
            row.setBucket(bucket);
            row.setDirPrefix(dirPrefix);
            row.setUrlExpireSeconds(expire);
            row.setEnabled(enabled ? 1 : 0);
            if (hasAccessKeyId) {
                row.setAccessKeyIdCipher(cipher.encrypt(req.accessKeyId().strip(), row.getId()));
                row.setAccessKeySecretCipher(cipher.encrypt(req.accessKeySecret().strip(), row.getId()));
            }
            mapper.updateById(row);
        }
        return view();
    }

    private StorageConfig findRow() {
        return mapper.selectOne(new QueryWrapper<StorageConfig>()
                .eq("provider", PROVIDER_ALIYUN_OSS)
                .last("limit 1"));
    }

    private ResolvedOss envResolved() {
        ResumeProperties.Oss env = properties.getOss();
        if (!complete(env.getEndpoint(), env.getBucket())
                || !complete(env.getAccessKeyId(), env.getAccessKeySecret())) {
            return null;
        }
        return new ResolvedOss(env.getEndpoint().strip(), env.getBucket().strip(),
                dirPrefix(env.getDirPrefix()), expire(env.getUrlExpireSeconds()),
                env.getAccessKeyId().strip(), env.getAccessKeySecret().strip());
    }

    private static String required(String value, String field) {
        if (isBlank(value)) {
            throw new BizException(ErrorCode.BAD_REQUEST, field + " 不能为空");
        }
        String trimmed = value.strip();
        if (trimmed.length() > 255) {
            throw new BizException(ErrorCode.BAD_REQUEST, field + " 过长");
        }
        return trimmed;
    }

    private static String dirPrefix(String value) {
        if (isBlank(value)) {
            return DEFAULT_DIR_PREFIX;
        }
        String prefix = value.strip().replaceAll("^/+|/+$", "");
        if (prefix.isEmpty()) {
            return DEFAULT_DIR_PREFIX;
        }
        if (prefix.length() > 128) {
            throw new BizException(ErrorCode.BAD_REQUEST, "dirPrefix 过长");
        }
        return prefix;
    }

    private static int expire(Integer value) {
        if (value == null) {
            return 3600;
        }
        if (value < MIN_EXPIRE_SECONDS || value > MAX_EXPIRE_SECONDS) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "签名有效期需在 " + MIN_EXPIRE_SECONDS + "~" + MAX_EXPIRE_SECONDS + " 秒之间");
        }
        return value;
    }

    private static boolean complete(String first, String second) {
        return !isBlank(first) && !isBlank(second);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.strip();
    }

    /**
     * 解析后的 OSS 配置,只在进程内使用,不返回给前端。
     */
    public record ResolvedOss(String endpoint, String bucket, String dirPrefix,
                              int urlExpireSeconds, String accessKeyId, String accessKeySecret) {
    }
}
