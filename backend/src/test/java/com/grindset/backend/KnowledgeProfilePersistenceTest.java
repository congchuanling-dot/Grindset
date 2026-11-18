package com.grindset.backend;

import com.grindset.backend.common.SnowflakeIdGenerator;
import com.grindset.backend.dto.KnowledgeProfileView;
import com.grindset.backend.entity.auth.User;
import com.grindset.backend.mapper.auth.UserMapper;
import com.grindset.backend.service.KnowledgeProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 掌握度画像真库测试:建用户 → 沉淀 → 查询 → EMA 更新 → 归一合并。
 * 测试事务整体回滚,不污染本地数据。
 */
@SpringBootTest
@Transactional
class KnowledgeProfilePersistenceTest {

    @Autowired
    private KnowledgeProfileService profileService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private SnowflakeIdGenerator idGenerator;

    @Test
    void recordCreatesAndUpdatesState() {
        long userId = createUser();

        profileService.record(userId, 1001L, List.of("Redis 持久化", "MySQL 索引"), 3);

        KnowledgeProfileView first = profileService.profile(userId, null, null, 10);
        assertEquals(2, first.summary().total());
        assertEquals(50, first.summary().averageMastery());

        KnowledgeProfileView.StateView redis = first.items().stream()
                .filter(item -> "Redis 持久化".equals(item.name()))
                .findFirst().orElseThrow();
        assertEquals(50, redis.mastery());
        assertEquals(1, redis.practiceCount());

        // 50 * 0.7 + 0 * 0.3 = 35,趋势应为下降
        profileService.record(userId, 1002L, List.of("Redis 持久化"), 1);

        KnowledgeProfileView.StateView updated = profileService.profile(userId, null, null, 10).items().stream()
                .filter(item -> "Redis 持久化".equals(item.name()))
                .findFirst().orElseThrow();
        assertEquals(35, updated.mastery());
        assertEquals(2, updated.practiceCount());
        assertEquals(1, updated.correctCount());
        assertEquals("DOWN", updated.trend());
    }

    @Test
    void normalizedNamesMergeIntoSameKnowledgePoint() {
        long userId = createUser();

        profileService.record(userId, 2001L, List.of("Redis 持久化"), 5);
        profileService.record(userId, 2002L, List.of("redis持久化"), 5);

        KnowledgeProfileView profile = profileService.profile(userId, null, null, 10);
        assertEquals(1, profile.summary().total());
        assertEquals(2, profile.items().get(0).practiceCount());
        assertNotNull(profile.items().get(0).knowledgePointId());
    }

    @Test
    void weightedCandidatesExcludeCoveredPoints() {
        long userId = createUser();

        profileService.record(userId, 3001L, List.of("Redis 持久化", "MySQL 索引"), 2);

        List<KnowledgeProfileService.Candidate> candidates =
                profileService.weightedCandidates(userId, List.of("redis持久化"), 5);

        assertEquals(1, candidates.size());
        assertEquals("MySQL 索引", candidates.get(0).name());
    }

    private long createUser() {
        User user = new User();
        user.setId(idGenerator.nextId());
        user.setNickname("画像测试用户");
        user.setStatus(0);
        assertEquals(1, userMapper.insert(user));
        return user.getId();
    }
}
