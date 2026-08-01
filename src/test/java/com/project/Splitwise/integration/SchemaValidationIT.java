package com.project.Splitwise.integration;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The cheapest possible guard against entity/schema drift.
 *
 * <p>With {@code ddl-auto=validate}, the application context simply will not start if the
 * Flyway migration and the JPA entities disagree about a table, column or type. Booting the
 * context therefore <em>is</em> the assertion — this test exists so that failure shows up
 * as one obvious red test rather than as a confusing cascade across every other IT.
 */
class SchemaValidationIT extends AbstractIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("Flyway migration satisfies Hibernate schema validation")
    void migratedSchemaMatchesEntityModel() {
        assertNotNull(entityManager);
    }
}
