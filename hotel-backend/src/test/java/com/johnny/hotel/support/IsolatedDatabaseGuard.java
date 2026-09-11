package com.johnny.hotel.support;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import java.sql.DriverManager;

/** Runs before the context creates DataSource/Flyway. Fail closed, including accidental contextLoads. */
public class IsolatedDatabaseGuard implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override public void initialize(ConfigurableApplicationContext context) {
        if (context.getEnvironment().getProperty("hotel.wallet.dev.fixture",Boolean.class,false)) {
            WalletDevelopmentGuard.verify(context);
            return;
        }
        if (!Boolean.getBoolean("hotel.mysql.tests")) throw new IllegalStateException("MySQL integration tests require explicit -Dhotel.mysql.tests=true");
        var env = context.getEnvironment();
        String url = env.getProperty("spring.datasource.url", "");
        if (!url.startsWith("jdbc:mysql://localhost:33079/hotel_lifecycle_test?")) throw new IllegalStateException("Refusing non-isolated test database URL");
        if (env.getProperty("spring.flyway.url") != null) throw new IllegalStateException("Separate Flyway URL is forbidden in tests");
        try (var connection = DriverManager.getConnection(url, env.getProperty("spring.datasource.username"), env.getProperty("spring.datasource.password"));
             var statement = connection.createStatement(); var result = statement.executeQuery("SELECT @@port, @@datadir, DATABASE()")) {
            result.next();
            if (result.getInt(1) != 33079 || !result.getString(2).replace('\\', '/').contains("/.lifecycle-test-mysql/data/")
                    || !"hotel_lifecycle_test".equals(result.getString(3))) throw new IllegalStateException("Refusing database without isolated instance identity");
        } catch (java.sql.SQLException e) { throw new IllegalStateException("Isolated test MySQL unavailable; no fallback permitted", e); }
    }
}
