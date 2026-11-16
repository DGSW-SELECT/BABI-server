package com.select.domain.user.photo;

import com.select.BabiServerApplication;
import java.sql.DriverManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import static org.junit.jupiter.api.Assertions.*;

class ProfilePhotoSchemaValidationTest {
    @Test
    void startupFailsBeforeServingRequestsWhenPhotoTableIsMissing() throws Exception {
        String url = "jdbc:h2:mem:profile-missing-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-test.sql"));
            try (var statement = connection.createStatement()) {
                statement.execute("DROP TABLE user_profile_photos");
            }
        }
        Exception failure = assertThrows(Exception.class, () -> new SpringApplicationBuilder(BabiServerApplication.class)
                .profiles("test")
                .web(WebApplicationType.NONE)
                .run("--spring.datasource.url=" + url, "--spring.datasource.username=sa", "--spring.datasource.password=",
                        "--spring.sql.init.mode=never", "--spring.jpa.hibernate.ddl-auto=validate"));
        StringBuilder messages = new StringBuilder();
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) messages.append(cause.getMessage()).append('\n');
        assertTrue(messages.toString().contains("user_profile_photos"), messages.toString());
    }
}
