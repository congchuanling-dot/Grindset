package com.grindset.backend.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 数据库敏感凭证的统一对称加密。AES-256-GCM,每条密文独立随机 IV,
 * AAD 绑定业务行 ID,防止把 A 行的密文搬到 B 行也能用。
 *
 * 主密钥来自 SECRET_MASTER_KEY;没配则用 JWT 密钥派生
 * (仅方便本地开发,生产必须单独配置,否则换 JWT 密钥会导致已存密文全部解不开)。
 */
@Component
public class SecretCipher {

    private static final Logger log = LoggerFactory.getLogger(SecretCipher.class);

    private static final String PREFIX = "v1:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec key;

    public SecretCipher(@Value("${app.secrets.master-key:}") String masterKey,
                        @Value("${app.jwt.secret}") String jwtSecret) {
        boolean fallback = masterKey == null || masterKey.isBlank();
        if (fallback) {
            log.warn("未配置 SECRET_MASTER_KEY,改用 JWT 密钥派生主密钥;生产环境请单独配置");
        }
        String seed = fallback ? jwtSecret : masterKey;
        this.key = new SecretKeySpec(sha256(seed), "AES");
    }

    /** 加密并返回可直接入库的字符串 */
    public String encrypt(String plain, long aad) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aadBytes(aad));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));

            byte[] packed = ByteBuffer.allocate(iv.length + encrypted.length)
                    .put(iv).put(encrypted).array();
            return PREFIX + Base64.getEncoder().encodeToString(packed);
        } catch (Exception e) {
            throw new LlmException(500, "加密 API Key 失败");
        }
    }

    /** 解密入库的字符串,失败说明主密钥变了 */
    public String decrypt(String stored, long aad) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            throw new LlmException(500, "API Key 密文格式不正确");
        }
        try {
            byte[] packed = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] iv = new byte[IV_LENGTH];
            byte[] encrypted = new byte[packed.length - IV_LENGTH];
            System.arraycopy(packed, 0, iv, 0, IV_LENGTH);
            System.arraycopy(packed, IV_LENGTH, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(aadBytes(aad));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new LlmException(500, "解密凭证失败,请检查 SECRET_MASTER_KEY 是否被更换");
        }
    }

    /** 展示用掩码:保留前 4 位和后 4 位 */
    public static String mask(String plain) {
        String value = plain == null ? "" : plain.strip();
        if (value.length() <= 8) {
            return "****";
        }
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }

    private static byte[] aadBytes(long aad) {
        return ByteBuffer.allocate(Long.BYTES).putLong(aad).array();
    }

    private static byte[] sha256(String seed) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(seed.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("初始化加密组件失败", e);
        }
    }
}
