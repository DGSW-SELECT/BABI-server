package com.select.domain.baby.repository;

import com.select.domain.baby.entity.Baby;
import com.select.domain.baby.entity.BabyMember;
import com.select.domain.baby.entity.BabyPermission;
import com.select.domain.baby.entity.BabyRole;
import com.select.domain.user.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ActiveProfiles("test")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:baby-member-repository;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=validate"
})
@Transactional
class BabyMemberRepositoryTest {

    @Autowired private EntityManager entityManager;
    @Autowired private BabyMemberRepository repository;

    private Long userId;
    private Long babyId;

    @BeforeEach
    void setUp() {
        User user = new User("care-access@example.invalid", "test-only-hash", "보호자");
        entityManager.persist(user);
        userId = user.getId();
        babyId = createBaby("첫째");
    }

    @Test
    void ownerWithoutExplicitPermissionHasAccess() {
        createMember(babyId, userId, BabyRole.OWNER);
        flushAndClear();

        assertTrue(repository.hasCareAccess(babyId, userId));
    }

    @Test
    void guardianWithCareAllHasAccess() {
        BabyMember member = createMember(babyId, userId, BabyRole.GUARDIAN);
        entityManager.persist(new BabyPermission(babyId, member.getId(), BabyPermission.CARE_ALL));
        flushAndClear();

        assertTrue(repository.hasCareAccess(babyId, userId));
    }

    @Test
    void guardianWithoutPermissionHasNoAccess() {
        createMember(babyId, userId, BabyRole.GUARDIAN);
        flushAndClear();

        assertFalse(repository.hasCareAccess(babyId, userId));
    }

    @Test
    void unrelatedPermissionDoesNotGrantCareAccess() {
        BabyMember member = createMember(babyId, userId, BabyRole.GUARDIAN);
        entityManager.persist(new BabyPermission(babyId, member.getId(), "OTHER_PERMISSION"));
        flushAndClear();

        assertFalse(repository.hasCareAccess(babyId, userId));
    }

    @ParameterizedTest
    @EnumSource(value = BabyRole.class, names = {"OWNER", "GUARDIAN"})
    void removedMemberHasNoAccessEvenWithPermission(BabyRole role) {
        BabyMember member = createMember(babyId, userId, role);
        entityManager.persist(new BabyPermission(babyId, member.getId(), BabyPermission.CARE_ALL));
        member.deactivate();
        flushAndClear();

        assertFalse(repository.hasCareAccess(babyId, userId));
    }

    @Test
    void permissionOnAnotherBabyDoesNotGrantAccess() {
        createMember(babyId, userId, BabyRole.GUARDIAN);
        Long otherBabyId = createBaby("둘째");
        BabyMember otherMember = createMember(otherBabyId, userId, BabyRole.GUARDIAN);
        entityManager.persist(new BabyPermission(otherBabyId, otherMember.getId(), BabyPermission.CARE_ALL));
        flushAndClear();

        assertFalse(repository.hasCareAccess(babyId, userId));
        assertTrue(repository.hasCareAccess(otherBabyId, userId));
    }

    @Test
    void anotherUsersPermissionDoesNotGrantAccess() {
        createMember(babyId, userId, BabyRole.GUARDIAN);
        User otherUser = new User("other-care-access@example.invalid", "test-only-hash", "다른 보호자");
        entityManager.persist(otherUser);
        BabyMember otherMember = createMember(babyId, otherUser.getId(), BabyRole.GUARDIAN);
        entityManager.persist(new BabyPermission(babyId, otherMember.getId(), BabyPermission.CARE_ALL));
        Long otherUserId = otherUser.getId();
        flushAndClear();

        assertFalse(repository.hasCareAccess(babyId, userId));
        assertTrue(repository.hasCareAccess(babyId, otherUserId));
    }

    @Test
    void nonMemberHasNoAccess() {
        flushAndClear();

        assertFalse(repository.hasCareAccess(babyId, userId));
    }

    private Long createBaby(String name) {
        Baby baby = Baby.builder().name(name).birthDate(LocalDate.of(2025, 1, 1))
                .createdByUserId(userId).build();
        entityManager.persist(baby);
        return baby.getId();
    }

    private BabyMember createMember(Long targetBabyId, Long targetUserId, BabyRole role) {
        BabyMember member = BabyMember.builder().babyId(targetBabyId).userId(targetUserId).role(role).build();
        entityManager.persist(member);
        return member;
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
