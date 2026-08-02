package com.project.Splitwise.tools;

import com.project.Splitwise.model.Balance;
import com.project.Splitwise.model.Expense;
import com.project.Splitwise.model.ExpenseGroup;
import com.project.Splitwise.model.ExpenseShare;
import com.project.Splitwise.model.IdempotencyRecord;
import com.project.Splitwise.model.GroupMember;
import com.project.Splitwise.model.User;
import com.project.Splitwise.model.OutboxEvent;
import com.project.Splitwise.model.Payment;
import com.project.Splitwise.model.PoisonMessage;
import com.project.Splitwise.model.ProcessedEvent;
import com.project.Splitwise.readmodel.GroupBalanceView;
import com.project.Splitwise.readmodel.SettlementView;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.boot.spi.MetadataImplementor;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.tool.schema.spi.SchemaManagementToolCoordinator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.orm.jpa.hibernate.SpringImplicitNamingStrategy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Emits the DDL Hibernate expects for the current entity model, using the same dialect and
 * naming strategies as the running application but without opening a connection.
 *
 * <p>The Flyway migration is derived from this output rather than written by hand, which
 * is what keeps {@code spring.jpa.hibernate.ddl-auto=validate} from failing at boot on a
 * column name or precision nobody noticed drifting.
 *
 * <p>Not an assertion test — it regenerates {@code target/generated-schema.sql} so the
 * migration can be diffed against it whenever the entities change.
 */
class SchemaDdlGenerator {

    @Test
    void generatePostgresDdl() throws Exception {
        Map<String, Object> settings = new HashMap<>();
        settings.put(AvailableSettings.DIALECT, "org.hibernate.dialect.PostgreSQLDialect");
        // No database is running; stop Hibernate reaching for JDBC metadata it cannot get.
        settings.put("hibernate.boot.allow_jdbc_metadata_access", "false");
        settings.put(AvailableSettings.PHYSICAL_NAMING_STRATEGY, new CamelCaseToUnderscoresNamingStrategy());
        settings.put(AvailableSettings.IMPLICIT_NAMING_STRATEGY, new SpringImplicitNamingStrategy());

        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySettings(settings)
                .build();

        try {
            MetadataImplementor metadata = (MetadataImplementor) new MetadataSources(registry)
                    .addAnnotatedClass(Expense.class)
                    .addAnnotatedClass(ExpenseShare.class)
                    .addAnnotatedClass(Balance.class)
                    .addAnnotatedClass(Payment.class)
                    .addAnnotatedClass(IdempotencyRecord.class)
                    .addAnnotatedClass(User.class)
                    .addAnnotatedClass(ExpenseGroup.class)
                    .addAnnotatedClass(GroupMember.class)
                    .addAnnotatedClass(ProcessedEvent.class)
                    .addAnnotatedClass(OutboxEvent.class)
                    .addAnnotatedClass(PoisonMessage.class)
                    .addAnnotatedClass(GroupBalanceView.class)
                    .addAnnotatedClass(SettlementView.class)
                    .buildMetadata();

            Path target = Path.of("target", "generated-schema.sql");
            Files.createDirectories(target.getParent());
            Files.deleteIfExists(target);

            Map<String, Object> exportSettings = new HashMap<>(settings);
            exportSettings.put("jakarta.persistence.schema-generation.scripts.action", "create");
            exportSettings.put("jakarta.persistence.schema-generation.scripts.create-target", target.toString());

            SchemaManagementToolCoordinator.process(metadata, registry, exportSettings, action -> {
            });

            System.out.println("=== GENERATED DDL ===");
            System.out.println(Files.readString(target));
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
