package com.renan.taskmanager.common.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuntimeConfigurationTest {

    @Test
    @DisplayName("Should prefer JDBC_DATABASE_URL and PORT")
    void shouldUsePlatformEnvironmentVariables() throws Exception {
        PropertySourcesPropertyResolver resolver = resolverWith(Map.of(
                "JDBC_DATABASE_URL", "jdbc:postgresql://neon.example/demo?sslmode=require",
                "PORT", "10000",
                "SERVER_PORT", "8081"));

        assertThat(resolver.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://neon.example/demo?sslmode=require");
        assertThat(resolver.getProperty("server.port")).isEqualTo("10000");
    }

    @Test
    @DisplayName("Should preserve the existing database and server variable fallbacks")
    void shouldPreserveExistingFallbacks() throws Exception {
        PropertySourcesPropertyResolver resolver = resolverWith(Map.of(
                "DB_HOST", "postgres",
                "DB_PORT", "5433",
                "DB_NAME", "taskmanager",
                "SERVER_PORT", "8081"));

        assertThat(resolver.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://postgres:5433/taskmanager");
        assertThat(resolver.getProperty("server.port")).isEqualTo("8081");
    }

    private PropertySourcesPropertyResolver resolverWith(Map<String, Object> overrides)
            throws Exception {
        MutablePropertySources sources = new MutablePropertySources();
        sources.addFirst(new MapPropertySource("test", overrides));
        new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"))
                .forEach(sources::addLast);
        return new PropertySourcesPropertyResolver(sources);
    }
}
