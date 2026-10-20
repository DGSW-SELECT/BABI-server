package com.select.domain.user.service;

import com.select.domain.user.dto.request.SignupRequest;
import com.select.domain.user.entity.User;
import com.select.domain.user.exception.DuplicateEmailException;
import com.select.domain.user.repository.UserRepository;
import org.hibernate.dialect.H2Dialect;
import org.hibernate.dialect.MySQLDialect;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringBootTest
class UserEmailConstraintTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @Transactional
    void h2DuplicateEmailIsConvertedToDuplicateEmailException() {
        String email = "constraint-test@example.com";
        userRepository.saveAndFlush(user(email));

        DataIntegrityViolationException exception = assertThrows(
                DataIntegrityViolationException.class,
                () -> userRepository.saveAndFlush(user(email))
        );

        assertDuplicateEmailConversion(exception, email);
    }

    @ParameterizedTest
    @MethodSource("mysqlEmailConstraintNames")
    void mysqlDialectExtractsQualifiedEmailConstraintName(String constraintName) {
        var sqlException = new SQLIntegrityConstraintViolationException(
                "Duplicate entry 'constraint-test@example.com' for key '" + constraintName + "'",
                "23000",
                1062
        );
        var converted = new MySQLDialect().buildSQLExceptionConversionDelegate()
                .convert(sqlException, "duplicate email", "insert into users");
        var violation = assertInstanceOf(ConstraintViolationException.class, converted);
        assertEquals(constraintName, violation.getConstraintName());

        assertDuplicateEmailConversion(
                new DataIntegrityViolationException("duplicate email", violation),
                "constraint-test@example.com"
        );
    }

    @Test
    void h2LegacyEmailConstraintIsConvertedToDuplicateEmailException() throws SQLException {
        try (var connection = DriverManager.getConnection(
                "jdbc:h2:mem:legacy-email-constraint;MODE=MySQL;DATABASE_TO_LOWER=TRUE"
        ); var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE users (email varchar(320), CONSTRAINT "
                    + User.LEGACY_EMAIL_UNIQUE_CONSTRAINT + " UNIQUE (email))");
            String sql = "INSERT INTO users (email) VALUES ('constraint-test@example.com')";
            statement.executeUpdate(sql);
            SQLException exception = assertThrows(SQLException.class, () -> statement.executeUpdate(sql));
            String name = new H2Dialect().getViolatedConstraintNameExtractor().extractConstraintName(exception);
            assertTrue(name.startsWith("public." + User.LEGACY_EMAIL_UNIQUE_CONSTRAINT + " INDEX "));

            assertDuplicateEmailConversion(new DataIntegrityViolationException(
                    "duplicate email", new ConstraintViolationException("duplicate email", exception, sql, name)
            ), "constraint-test@example.com");
        }
    }

    private static Stream<String> mysqlEmailConstraintNames() {
        return Stream.of(
                "users." + User.LEGACY_EMAIL_UNIQUE_CONSTRAINT,
                "users." + User.EMAIL_UNIQUE_CONSTRAINT
        );
    }

    private void assertDuplicateEmailConversion(DataIntegrityViolationException exception, String email) {
        UserRepository repository = mock(UserRepository.class);
        when(repository.saveAndFlush(any(User.class))).thenThrow(exception);
        UserService service = new UserService(repository, passwordEncoder);

        assertThrows(DuplicateEmailException.class, () -> service.signup(
                new SignupRequest(email, "Test1234!", "Test1234!", "tester")
        ));
    }

    private User user(String email) {
        return User.builder()
                .email(email)
                .passwordHash("test-password-hash")
                .nickname("tester")
                .build();
    }
}
