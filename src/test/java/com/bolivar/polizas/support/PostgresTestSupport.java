package com.bolivar.polizas.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base para pruebas que necesitan PostgreSQL real (las migraciones usan JSONB,
 * triggers PL/pgSQL e índices parciales, por lo que H2 no es representativo).
 *
 * <p>Por defecto levanta un contenedor con Testcontainers (requiere Docker).
 * Si se define la variable {@code POLIZAS_TEST_DB_URL} (con
 * {@code POLIZAS_TEST_DB_USERNAME} y {@code POLIZAS_TEST_DB_PASSWORD}), usa esa
 * base de datos existente; útil en CI sin Docker. La base debe estar vacía.</p>
 */
public abstract class PostgresTestSupport {

    private static PostgreSQLContainer<?> container;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        String externalUrl = System.getenv("POLIZAS_TEST_DB_URL");
        if (externalUrl != null && !externalUrl.isBlank()) {
            registry.add("spring.datasource.url", () -> externalUrl);
            registry.add("spring.datasource.username", () -> System.getenv("POLIZAS_TEST_DB_USERNAME"));
            registry.add("spring.datasource.password", () -> System.getenv("POLIZAS_TEST_DB_PASSWORD"));
            return;
        }
        PostgreSQLContainer<?> pg = startContainer();
        registry.add("spring.datasource.url", pg::getJdbcUrl);
        registry.add("spring.datasource.username", pg::getUsername);
        registry.add("spring.datasource.password", pg::getPassword);
    }

    private static synchronized PostgreSQLContainer<?> startContainer() {
        if (container == null) {
            container = new PostgreSQLContainer<>("postgres:16-alpine");
            container.start();
        }
        return container;
    }
}
