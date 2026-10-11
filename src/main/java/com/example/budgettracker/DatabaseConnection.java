package com.example.budgettracker;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Opens a MySQL connection using credentials supplied by the environment. */
public final class DatabaseConnection {

    private DatabaseConnection() {
    }

    public static Connection open() throws SQLException {
        String url = requiredEnvironmentVariable("BUDGET_DB_URL");
        String user = requiredEnvironmentVariable("BUDGET_DB_USER");
        String password = System.getenv("BUDGET_DB_PASSWORD");

        if (password == null) {
            throw new IllegalStateException(
                "Missing BUDGET_DB_PASSWORD. Set the database connection values in this Terminal first."
            );
        }

        return DriverManager.getConnection(url, user, password);
    }

    private static String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);

        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                "Missing " + name + ". Set the database connection values in this Terminal first."
            );
        }

        return value;
    }
}
