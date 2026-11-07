package com.select.domain.baby.service;

import com.select.domain.baby.entity.BabyMember;
import com.select.domain.report.repository.BabyAccessRepository;
import com.select.domain.baby.entity.BabyRole;
import com.select.domain.baby.repository.BabyMemberRepository;
import com.select.domain.baby.request.BabyCreateRequest;
import com.select.domain.auth.mail.EmailSender;
import com.select.domain.user.entity.User;
import com.select.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.sql.DriverManager;
import java.util.UUID;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named = "BABI_ISOLATED_MYSQL_TEST", matches = "enabled")
class BabyInvitationMySqlIntegrationTest extends BabyInvitationIntegrationTest {
    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) {
        String port = System.getenv("BABI_ISOLATED_MYSQL_PORT");
        String password = System.getenv("BABI_ISOLATED_MYSQL_PASSWORD");
        String marker = System.getenv("BABI_ISOLATED_MYSQL_MARKER");
        if (port == null || !port.matches("[0-9]+") || Integer.parseInt(port) <= 0
                || Integer.parseInt(port) > 65535 || "3307".equals(port)
                || password == null || password.isBlank() || marker == null || !marker.matches("[0-9a-f]{48}")) {
            throw new IllegalStateException("Isolated MySQL test port and credentials are required.");
        }
        String url = "jdbc:mysql://127.0.0.1:" + port + "/babi_invitation_isolated?connectionTimeZone=UTC";
        try (var connection = DriverManager.getConnection(url, "babi_isolated_tester", password);
             var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT run_token FROM isolation_guard")) {
            if (!result.next() || !marker.equals(result.getString(1)) || result.next()) {
                throw new IllegalStateException("Isolated MySQL marker did not match.");
            }
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("Cannot validate isolated MySQL test marker.");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> "babi_isolated_tester");
        registry.add("spring.datasource.password", () -> password);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.sql.init.mode", () -> "never");
    }

    @Autowired private JdbcTemplate mysqlJdbc;
    @Autowired private BabyAccessRepository reportAccess;
    @Autowired private UserRepository mysqlUsers;
    @Autowired private BabyMemberRepository mysqlMembers;
    @Autowired private BabyService mysqlBabies;
    @Autowired private BabyInvitationWorkflow mysqlInvitations;
    @Autowired private PlatformTransactionManager mysqlTransactions;
    @Autowired private EmailSender mysqlSender;

    @Test
    void codeJoinGrantsReportAccessAndLeaveRevokesIt() {
        var fixture = fixture();
        Long joiningUser = user();
        String code = mysqlInvitations.getCode(fixture.babyId, fixture.ownerId).code();
        mysqlInvitations.accept(code, joiningUser);
        assertTrue(reportAccess.hasAccess(joiningUser, fixture.babyId));
        mysqlBabies.leaveBaby(fixture.babyId, joiningUser);
        assertFalse(reportAccess.hasAccess(joiningUser, fixture.babyId));
    }

    @Test
    void usesOnlyIsolatedMysqlAndExpectedSchemaConstraints() {
        assertEquals("babi_invitation_isolated", mysqlJdbc.queryForObject("SELECT DATABASE()", String.class));
        assertTrue(mysqlJdbc.queryForObject("SELECT VERSION()", String.class).startsWith("8.4."));
        assertEquals(6, mysqlJdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('users','babies','baby_members','baby_invitation_codes','allocated_baby_invitation_codes','baby_email_invitations')", Integer.class));
        assertEquals(2, mysqlJdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND ((table_name='baby_invitation_codes' AND column_name='max_uses') OR (table_name='baby_email_invitations' AND column_name='token_hash')) AND is_nullable='NO'", Integer.class));
        assertEquals(2, mysqlJdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name IN ('baby_invitation_codes','allocated_baby_invitation_codes') AND column_name='code' AND column_type='varchar(32)'", Integer.class));
        assertEquals(1, mysqlJdbc.queryForObject("SELECT COUNT(*) FROM information_schema.table_constraints WHERE table_schema=DATABASE() AND constraint_name='uk_email_invitation_pending' AND constraint_type='UNIQUE'", Integer.class));
    }

    @Test
    void mysqlRejectsInvalidForeignKeysNullsAndDuplicateEmailInvitations() {
        var fixture = fixture();
        mysqlInvitations.send(fixture.babyId, fixture.ownerId, fixture.targetEmail);
        rejectAndRollback("INSERT INTO baby_email_invitations (baby_id, invited_by_member_id, invited_email, role, token_hash, expires_at) VALUES (?, ?, ?, 'GUARDIAN', ?, CURRENT_TIMESTAMP)",
                -1L, -1L, "nonexistent@example.invalid", "a".repeat(64));
        rejectAndRollback("INSERT INTO baby_email_invitations (baby_id, invited_by_member_id, invited_email, role, token_hash, expires_at) SELECT baby_id, invited_by_member_id, invited_email, role, ?, expires_at FROM baby_email_invitations WHERE baby_id = ?", "b".repeat(64), fixture.babyId);
        rejectAndRollback("INSERT INTO baby_members (baby_id, user_id, role) VALUES (?, ?, ?)", fixture.babyId, fixture.ownerId, "GUARDIAN");
        rejectAndRollback("INSERT INTO allocated_baby_invitation_codes (code) VALUES (NULL)");
        assertEquals(1, mysqlJdbc.queryForObject("SELECT COUNT(*) FROM baby_email_invitations WHERE baby_id = ?", Integer.class, fixture.babyId));
        assertEquals(2, mysqlJdbc.queryForObject("SELECT COUNT(*) FROM baby_members WHERE baby_id = ?", Integer.class, fixture.babyId));
    }

    private void rejectAndRollback(String sql, Object... arguments) {
        new TransactionTemplate(mysqlTransactions).executeWithoutResult(status -> {
            assertThrows(DataIntegrityViolationException.class, () -> mysqlJdbc.update(sql, arguments));
            status.setRollbackOnly();
        });
    }

    @Test
    void ownershipTransferBeforeConcurrentSendRejectsPreviousOwner() throws Exception {
        var fixture = fixture();
        serialized(() -> mysqlBabies.transferOwnership(fixture.babyId, fixture.ownerId, fixture.memberId),
                () -> assertThrows(AccessDeniedException.class,
                        () -> mysqlInvitations.send(fixture.babyId, fixture.ownerId, fixture.targetEmail)));
        assertEquals(BabyRole.OWNER, mysqlMembers.findByBabyIdAndUserId(fixture.babyId, fixture.memberId).orElseThrow().getRole());
        assertEquals(0, mysqlJdbc.queryForObject("SELECT COUNT(*) FROM baby_email_invitations WHERE baby_id = ?", Integer.class, fixture.babyId));
    }

    @Test
    void invitationCommittedBeforeConcurrentTransferRemainsValid() throws Exception {
        var fixture = fixture();
        CountDownLatch sending = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(call -> {
            sending.countDown();
            assertTrue(release.await(8, TimeUnit.SECONDS));
            return null;
        }).when(mysqlSender).send(anyString(), anyString(), anyString());
        try (var executor = Executors.newFixedThreadPool(2)) {
            var send = executor.submit(() -> mysqlInvitations.send(fixture.babyId, fixture.ownerId, fixture.targetEmail));
            try {
                assertTrue(sending.await(8, TimeUnit.SECONDS));
                var transfer = executor.submit(() -> mysqlBabies.transferOwnership(fixture.babyId, fixture.ownerId, fixture.memberId));
                assertThrows(TimeoutException.class, () -> transfer.get(300, TimeUnit.MILLISECONDS));
                release.countDown();
                send.get(8, TimeUnit.SECONDS);
                transfer.get(8, TimeUnit.SECONDS);
            } finally { release.countDown(); }
        }
        assertEquals(BabyRole.OWNER, mysqlMembers.findByBabyIdAndUserId(fixture.babyId, fixture.memberId).orElseThrow().getRole());
        assertEquals(1, mysqlJdbc.queryForObject("SELECT COUNT(*) FROM baby_email_invitations WHERE baby_id = ? AND status = 'PENDING'", Integer.class, fixture.babyId));
        assertEquals(BabyRole.GUARDIAN, mysqlMembers.findByBabyIdAndUserId(fixture.babyId, fixture.ownerId).orElseThrow().getRole());
    }

    private Fixture fixture() {
        Long owner = user();
        Long member = user();
        Long baby = mysqlBabies.createBaby(new BabyCreateRequest("MySQL test", LocalDate.of(2025, 1, 1)), owner).babyId();
        mysqlMembers.saveAndFlush(BabyMember.builder().babyId(baby).userId(member).role(BabyRole.GUARDIAN).build());
        return new Fixture(baby, owner, member, UUID.randomUUID() + "@example.invalid");
    }

    private Long user() {
        return mysqlUsers.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.invalid")
                .passwordHash("test-only").nickname("test").build()).getId();
    }

    private void serialized(Runnable firstAction, Runnable secondAction) throws Exception {
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch started = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> new TransactionTemplate(mysqlTransactions).executeWithoutResult(status -> {
                firstAction.run();
                locked.countDown();
                try { assertTrue(release.await(8, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            }));
            try {
                assertTrue(locked.await(8, TimeUnit.SECONDS));
                var second = executor.submit(() -> { started.countDown(); secondAction.run(); });
                assertTrue(started.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> second.get(300, TimeUnit.MILLISECONDS));
                release.countDown();
                first.get(8, TimeUnit.SECONDS);
                second.get(8, TimeUnit.SECONDS);
            } finally { release.countDown(); }
        }
    }

    private record Fixture(Long babyId, Long ownerId, Long memberId, String targetEmail) {}
}
