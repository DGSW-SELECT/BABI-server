package com.select.domain.handover;

import com.select.support.IsolatedMySqlProperties;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@EnabledIfEnvironmentVariable(named = "BABI_ISOLATED_MYSQL_TEST", matches = "enabled")
class HandoverMemoMySqlIntegrationTest extends HandoverMemoIntegrationTest {

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) {
        IsolatedMySqlProperties.register(registry);
    }
}
