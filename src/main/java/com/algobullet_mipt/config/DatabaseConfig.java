package com.algobullet_mipt.config;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

@Configuration
@EnableConfigurationProperties(AppDatabaseProperties.class)
public class DatabaseConfig {

    private final DataSourceProperties dataSourceProperties;
    private final AppDatabaseProperties databaseProperties;

    public DatabaseConfig(DataSourceProperties dataSourceProperties, AppDatabaseProperties databaseProperties) {
        this.dataSourceProperties = dataSourceProperties;
        this.databaseProperties = databaseProperties;
    }

    @Bean
    @Primary
    public DataSource dataSource() {
        if (databaseProperties.isAutoCreate()) {
            PostgresConnectionDetails details = PostgresConnectionDetails.parse(
                    dataSourceProperties.getUrl(),
                    databaseProperties.getAdminDatabase()
            );
            try {
                createDatabaseIfMissing(details);
                createSchemaIfMissing(details, databaseProperties.getSchema());
            } catch (SQLException e) {
                throw new IllegalStateException("Failed to initialize database", e);
            }
        }
        return dataSourceProperties.initializeDataSourceBuilder().build();
    }

    private void createDatabaseIfMissing(PostgresConnectionDetails details) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                details.adminUrl(),
                dataSourceProperties.getUsername(),
                dataSourceProperties.getPassword()
        )) {
            if (!databaseExists(connection, details.database())) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("CREATE DATABASE \"" + details.database() + "\"");
                }
            }
        }
    }

    private void createSchemaIfMissing(PostgresConnectionDetails details, String schema) throws SQLException {
        if (schema == null || schema.isBlank()) {
            return;
        }
        try (Connection connection = DriverManager.getConnection(
                details.jdbcUrl(),
                dataSourceProperties.getUsername(),
                dataSourceProperties.getPassword()
        )) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE SCHEMA IF NOT EXISTS \"" + schema + "\"");
            }
        }
    }

    private boolean databaseExists(Connection connection, String database) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM pg_database WHERE datname = ?"
        )) {
            statement.setString(1, database);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private record PostgresConnectionDetails(String jdbcUrl, String adminUrl, String database) {

        private static final String PREFIX = "jdbc:postgresql://";

        static PostgresConnectionDetails parse(String jdbcUrl, String adminDatabase) {
            if (jdbcUrl == null || !jdbcUrl.startsWith(PREFIX)) {
                throw new IllegalArgumentException("Only PostgreSQL JDBC URLs are supported");
            }
            String withoutPrefix = jdbcUrl.substring(PREFIX.length());
            int slashIndex = withoutPrefix.indexOf('/');
            if (slashIndex < 0) {
                throw new IllegalArgumentException("JDBC URL must include database name");
            }
            String hostAndPort = withoutPrefix.substring(0, slashIndex);
            String dbAndParams = withoutPrefix.substring(slashIndex + 1);
            String database = dbAndParams;
            String params = "";
            int questionIndex = dbAndParams.indexOf('?');
            if (questionIndex >= 0) {
                database = dbAndParams.substring(0, questionIndex);
                params = dbAndParams.substring(questionIndex);
            }
            if (database.isBlank()) {
                throw new IllegalArgumentException("Database name cannot be empty");
            }
            String adminUrl = PREFIX + hostAndPort + "/" + adminDatabase + params;
            String primaryUrl = PREFIX + hostAndPort + "/" + database + params;
            return new PostgresConnectionDetails(primaryUrl, adminUrl, database);
        }
    }
}
