package com.grindset.backend;

import com.grindset.backend.common.SnowflakeIdGenerator;
import com.grindset.backend.entity.User;
import com.grindset.backend.entity.UserIdentity;
import com.grindset.backend.mapper.UserIdentityMapper;
import com.grindset.backend.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 端到端验证:雪花生成 ID → 插入 users/user_identities → 查回来。
 * 真连本地 grindset 库运行。
 */
@SpringBootTest
class AuthPersistenceTest {

    @Autowired
    private SnowflakeIdGenerator idGenerator;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private UserIdentityMapper identityMapper;

    @Test
    void snowflakeIdsAreUniqueAndIncreasing() {
        long a = idGenerator.nextId();
        long b = idGenerator.nextId();
        assertTrue(a > 0, "ID 应为正数");
        assertNotEquals(a, b, "连续两个 ID 不应相同");
        assertTrue(b > a, "后生成的 ID 应更大(趋势递增)");
    }

    @Test
    void insertAndReadBack() {
        // 建一个用户
        User user = new User();
        user.setId(idGenerator.nextId());
        user.setNickname("测试用户");
        user.setStatus(0);
        int inserted = userMapper.insert(user);
        assertEquals(1, inserted, "应插入 1 行 user");

        // 建它的一条 LOCAL 登录身份
        UserIdentity identity = new UserIdentity();
        identity.setId(idGenerator.nextId());
        identity.setUserId(user.getId());
        identity.setProvider("LOCAL");
        // 用时间戳保证本测试可重复跑,不触发唯一索引冲突
        identity.setIdentifier("test_" + System.currentTimeMillis());
        identity.setCredential("$2a$10$fakebcrypthashforplaceholder");
        assertEquals(1, identityMapper.insert(identity), "应插入 1 行 identity");

        // 查回来核对
        User got = userMapper.selectById(user.getId());
        assertNotNull(got, "应能按 id 查回 user");
        assertEquals("测试用户", got.getNickname());
        assertNotNull(got.getCreatedAt(), "created_at 应由数据库自动填充");

        UserIdentity gotIdentity = identityMapper.selectById(identity.getId());
        assertNotNull(gotIdentity);
        assertEquals(user.getId(), gotIdentity.getUserId(), "identity 应正确关联 user");
    }
}
