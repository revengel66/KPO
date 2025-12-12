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

@Component
public class MovementDateMigration implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(MovementDateMigration.class);

    private final DataSource dataSource;

    public MovementDateMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(String... args) {
        try (Connection connection = dataSource.getConnection()) {
            if (!tableExists(connection)) {
                return;
            }
            boolean migrated = false;
            if (requiresDateOnlyMigration(connection)) {
                performDateOnlyMigration(connection);
                migrated = true;
            }
            if (requiresEpochMigration(connection)) {
                performEpochMigration(connection);
                migrated = true;
            }
            if (requiresNumericEpochMigration(connection)) {
                performNumericEpochMigration(connection);
                migrated = true;
            }
            if (requiresSeparatorNormalization(connection)) {
                performSeparatorNormalization(connection);
                migrated = true;
            }
            if (migrated) {
                logger.info("Movement dates were migrated to ISO datetime format.");
            }
        } catch (SQLException exception) {
            logger.error("Failed to migrate movement dates", exception);
        }
    }

    private boolean tableExists(Connection connection) throws SQLException {
        String sql = "SELECT name FROM sqlite_master WHERE type='table' AND name='movements'";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            return rs.next();
        }
    }

    private boolean requiresDateOnlyMigration(Connection connection) throws SQLException {
        String sql = """
                SELECT 1 FROM movements
                WHERE date IS NOT NULL
                  AND (length(date) = 10 OR instr(date, 'T') = 0)
                LIMIT 1
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            return rs.next();
        }
    }

    private boolean requiresEpochMigration(Connection connection) throws SQLException {
        String sql = """
                SELECT 1 FROM movements
                WHERE date IS NOT NULL
                  AND date NOT LIKE '%-%'
                  AND instr(date, 'T') > 0
                LIMIT 1
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            return rs.next();
        }
    }

    private void performDateOnlyMigration(Connection connection) throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    UPDATE movements
                    SET date = substr(date, 1, 10) || ' 00:00:00'
                    WHERE date IS NOT NULL
                      AND (length(date) = 10 OR instr(date, 'T') = 0)
                    """);
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private void performEpochMigration(Connection connection) throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    UPDATE movements
                    SET date = strftime('%Y-%m-%d %H:%M:%S', datetime(substr(date, 1, instr(date, 'T') - 1), 'unixepoch'))
                    WHERE date IS NOT NULL
                      AND date NOT LIKE '%-%'
                      AND instr(date, 'T') > 0
                    """);
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private boolean requiresNumericEpochMigration(Connection connection) throws SQLException {
        String sql = """
                SELECT 1 FROM movements
                WHERE (typeof(date) IN ('integer','real'))
                   OR (typeof(date) = 'text' AND date GLOB '[0-9]*')
                LIMIT 1
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            return rs.next();
        }
    }

    private void performNumericEpochMigration(Connection connection) throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    UPDATE movements
                    SET date = strftime('%Y-%m-%d %H:%M:%S', datetime(CAST(date AS REAL)/1000, 'unixepoch'))
                    WHERE typeof(date) IN ('integer','real')
                       OR (typeof(date) = 'text' AND date GLOB '[0-9]*')
                    """);
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }

    private boolean requiresSeparatorNormalization(Connection connection) throws SQLException {
        String sql = """
                SELECT 1 FROM movements
                WHERE date LIKE '%T%'
                   OR date LIKE '%.%'
                LIMIT 1
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            return rs.next();
        }
    }

    private void performSeparatorNormalization(Connection connection) throws SQLException {
        boolean previousAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    UPDATE movements
                    SET date = strftime('%Y-%m-%d %H:%M:%S', replace(substr(date, 1, instr(date, '.') - 1), 'T', ' '))
                    WHERE date LIKE '%.%'
                    """);
            statement.executeUpdate("""
                    UPDATE movements
                    SET date = replace(date, 'T', ' ')
                    WHERE instr(date, 'T') > 0
                      AND instr(date, '.') = 0
                    """);
            connection.commit();
        } catch (SQLException exception) {
            connection.rollback();
            throw exception;
        } finally {
            connection.setAutoCommit(previousAutoCommit);
        }
    }
}
