package com.example.kpo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;

/**
 * Temporary migration that aligns the physical products table
 * with the current {@link com.example.kpo.entity.Product} entity.
 * An outdated database file still contains legacy columns
 * (`count` and `warehouse_id`) with NOT NULL constraints, which
 * makes INSERT statements fail. We drop the legacy definition
 * and recreate the table with the expected columns once on
 * application startup.
 */
@Component
public class ProductTableMigration implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(ProductTableMigration.class);

    private final DataSource dataSource;

    public ProductTableMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) {
        try (Connection connection = dataSource.getConnection()) {
            if (!tableExists(connection, "products")) {
                return;
            }
            if (!requiresMigration(connection)) {
                return;
            }
            migrateProductsTable(connection);
            logger.info("Products table schema was realigned with the Product entity.");
        } catch (SQLException exception) {
            logger.error("Failed to verify or migrate the products table schema", exception);
        }
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        final String sql = "SELECT name FROM sqlite_master WHERE type='table' AND name = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private boolean requiresMigration(Connection connection) throws SQLException {
        final String sql = "PRAGMA table_info(products)";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String columnName = resultSet.getString("name");
                if (columnName == null) {
                    continue;
                }
                String normalized = columnName.toLowerCase(Locale.ROOT);
                if ("count".equals(normalized) || "warehouse_id".equals(normalized)) {
                    return true;
                }
            }
            return false;
        }
    }

    private void migrateProductsTable(Connection connection) throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = OFF");
            statement.execute("ALTER TABLE products RENAME TO products_old");
            statement.execute("""
                    CREATE TABLE products (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        name VARCHAR(255) NOT NULL,
                        info VARCHAR(255),
                        category_id BIGINT NOT NULL,
                        CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES categories(id)
                    )
                    """);
            statement.execute("""
                    INSERT INTO products (id, name, info, category_id)
                    SELECT id, name, info, category_id FROM products_old
                    """);
            statement.execute("DROP TABLE products_old");
            statement.execute("PRAGMA foreign_keys = ON");
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            try (Statement enableKeys = connection.createStatement()) {
                enableKeys.execute("PRAGMA foreign_keys = ON");
            } catch (SQLException enableError) {
                logger.warn("Failed to re-enable foreign keys after products table migration", enableError);
            }
            connection.setAutoCommit(previousAutoCommit);
        }
    }
}
