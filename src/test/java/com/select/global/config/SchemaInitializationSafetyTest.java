package com.select.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SchemaInitializationSafetyTest {
    @Test
    void sharedProfilesDoNotInitializeOrUpdateSchemaByDefault() throws IOException {
        var loader = new YamlPropertySourceLoader();
        var base = loader.load("base", new ClassPathResource("application.yml")).getFirst();
        var local = loader.load("local", new ClassPathResource("application-local.yml")).getFirst();
        var aws = loader.load("aws", new ClassPathResource("application-aws.yml")).getFirst();
        var test = loader.load("test", new ClassPathResource("application-test.yml")).getFirst();
        assertEquals("never", base.getProperty("spring.sql.init.mode"));
        assertEquals("validate", local.getProperty("spring.jpa.hibernate.ddl-auto"));
        assertEquals("never", aws.getProperty("spring.sql.init.mode"));
        assertEquals("validate", aws.getProperty("spring.jpa.hibernate.ddl-auto"));
        assertEquals("always", test.getProperty("spring.sql.init.mode"));
    }
}
