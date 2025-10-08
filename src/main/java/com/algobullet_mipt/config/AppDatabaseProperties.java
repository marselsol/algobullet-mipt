package com.algobullet_mipt.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.database")
public class AppDatabaseProperties {

    private boolean autoCreate = true;
    private String adminDatabase = "postgres";
    private String schema = "algo";

    public boolean isAutoCreate() {
        return autoCreate;
    }

    public void setAutoCreate(boolean autoCreate) {
        this.autoCreate = autoCreate;
    }

    public String getAdminDatabase() {
        return adminDatabase;
    }

    public void setAdminDatabase(String adminDatabase) {
        this.adminDatabase = adminDatabase;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }
}
