package com.select.domain.handover;

import com.select.domain.baby.entity.Baby;
import com.select.domain.baby.entity.BabyMember;
import com.select.domain.baby.entity.BabyRole;
import com.select.domain.baby.repository.BabyMemberRepository;
import com.select.domain.baby.repository.BabyRepository;
import com.select.domain.user.entity.User;
import com.select.domain.user.repository.UserRepository;
import com.select.support.IsolatedMySqlProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@EnabledIfEnvironmentVariable(named = "BABI_ISOLATED_MYSQL_TEST", matches = "enabled")
@SpringBootTest
class HandoverMemoMySqlIntegrationTest {

    @Autowired private UserRepository users;
    @Autowired private BabyRepository babies;
    @Autowired private BabyMemberRepository members;
    @Autowired private JdbcTemplate jdbc;

    private Long babyId;
    private Long otherBabyId;
    private Long authorMemberId;

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) {
        IsolatedMySqlProperties.register(registry);
    }

    @BeforeEach
    void setUp() {
        Long userId = users.saveAndFlush(new User(
                UUID.randomUUID() + "@example.invalid", "test-only-hash", "보호자")).getId();
        babyId = babies.saveAndFlush(Baby.builder()
                .name("테스트 아기")
                .birthDate(LocalDate.of(2026, 1, 1))
                .createdByUserId(userId)
                .build()).getId();
        otherBabyId = babies.saveAndFlush(Baby.builder()
                .name("다른 아기")
                .birthDate(LocalDate.of(2026, 2, 1))
                .createdByUserId(userId)
                .build()).getId();
        authorMemberId = members.saveAndFlush(BabyMember.builder()
                .babyId(babyId)
                .userId(userId)
                .role(BabyRole.OWNER)
                .build()).getId();
    }

    @Test
    void acceptsAuthorBelongingToSameBaby() {
        assertDoesNotThrow(() -> insertMemo(babyId, authorMemberId, "정상 메모"));
    }

    @Test
    void rejectsAuthorBelongingToAnotherBaby() {
        assertThrows(DataAccessException.class,
                () -> insertMemo(otherBabyId, authorMemberId, "잘못된 작성자"));
    }

    @Test
    void rejectsBlankContent() {
        assertThrows(DataAccessException.class,
                () -> insertMemo(babyId, authorMemberId, "   "));
    }

    private void insertMemo(Long targetBabyId, Long targetAuthorMemberId, String content) {
        jdbc.update("INSERT INTO handover_memos(baby_id, author_member_id, content) VALUES (?, ?, ?)",
                targetBabyId, targetAuthorMemberId, content);
    }
}
