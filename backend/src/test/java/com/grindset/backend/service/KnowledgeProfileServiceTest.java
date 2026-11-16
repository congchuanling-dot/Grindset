package com.grindset.backend.service;

import com.grindset.backend.entity.UserKnowledgeState;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeProfileServiceTest {

    @Test
    void normalizeMergesPunctuationAndCase() {
        assertEquals(KnowledgeProfileService.normalize("Redis 持久化。"),
                KnowledgeProfileService.normalize("redis持久化"));
        assertEquals(KnowledgeProfileService.normalize("MySQL 索引"),
                KnowledgeProfileService.normalize("mysql索引！"));
    }

    @Test
    void observationMapsScoreToZeroToHundred() {
        assertEquals(0, KnowledgeProfileService.observation(1));
        assertEquals(50, KnowledgeProfileService.observation(3));
        assertEquals(100, KnowledgeProfileService.observation(5));
    }

    @Test
    void levelUsesMasteryThresholds() {
        assertEquals("WEAK", KnowledgeProfileService.levelOf(39));
        assertEquals("LEARNING", KnowledgeProfileService.levelOf(40));
        assertEquals("LEARNING", KnowledgeProfileService.levelOf(74));
        assertEquals("MASTERED", KnowledgeProfileService.levelOf(75));
    }

    @Test
    void weightPrefersWeakAndRecentlyWrongPoints() {
        LocalDateTime now = LocalDateTime.now();
        UserKnowledgeState weak = state(35, 2, now.minusDays(10));
        UserKnowledgeState strong = state(85, 5, now.minusDays(1));

        assertTrue(KnowledgeProfileService.weight(weak, now) > KnowledgeProfileService.weight(strong, now));
    }

    @Test
    void stalledWeakPointGetsExtraWeight() {
        LocalDateTime now = LocalDateTime.now();
        UserKnowledgeState recent = state(50, 4, now.minusDays(1));
        UserKnowledgeState stale = state(50, 4, now.minusDays(30));

        assertTrue(KnowledgeProfileService.weight(stale, now) > KnowledgeProfileService.weight(recent, now));
    }

    private static UserKnowledgeState state(int mastery, int lastScore, LocalDateTime practicedAt) {
        UserKnowledgeState state = new UserKnowledgeState();
        state.setMastery(mastery);
        state.setLastScore(lastScore);
        state.setLastPracticedAt(practicedAt);
        return state;
    }
}
