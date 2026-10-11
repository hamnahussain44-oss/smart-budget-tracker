package com.example.budgettracker;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Reads and saves the tracker's monthly budget limits in MySQL. */
public class BudgetRepository {

    public Map<String, Budget> findAll() throws SQLException {
        String sql = "SELECT category, monthly_limit FROM budgets ORDER BY category";
        Map<String, Budget> budgets = new LinkedHashMap<>();

        try (Connection connection = DatabaseConnection.open();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                String category = results.getString("category");
                BigDecimal limit = results.getBigDecimal("monthly_limit");
                budgets.put(categoryKey(category), new Budget(category, limit));
            }
        }

        return budgets;
    }

    public void save(Budget budget) throws SQLException {
        String sql = "INSERT INTO budgets (category, monthly_limit) VALUES (?, ?) "
            + "ON DUPLICATE KEY UPDATE monthly_limit = ?";

        try (Connection connection = DatabaseConnection.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, budget.getCategory());
            statement.setBigDecimal(2, budget.getLimit());
            statement.setBigDecimal(3, budget.getLimit());
            statement.executeUpdate();
        }
    }

    public int delete(String category) throws SQLException {
        String sql = "DELETE FROM budgets WHERE LOWER(category) = LOWER(?)";
        try (Connection connection = DatabaseConnection.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, category.trim());
            return statement.executeUpdate();
        }
    }

    public BigDecimal findOverallLimit() throws SQLException {
        try (Connection connection = DatabaseConnection.open()) {
            ensureOverallLimitTable(connection);
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT monthly_limit FROM overall_monthly_budget WHERE id = 1");
                 ResultSet results = statement.executeQuery()) {
                return results.next() ? results.getBigDecimal("monthly_limit") : null;
            }
        }
    }

    public void saveOverallLimit(BigDecimal limit) throws SQLException {
        try (Connection connection = DatabaseConnection.open()) {
            ensureOverallLimitTable(connection);
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO overall_monthly_budget (id, monthly_limit) VALUES (1, ?) "
                        + "ON DUPLICATE KEY UPDATE monthly_limit = VALUES(monthly_limit)")) {
                statement.setBigDecimal(1, limit);
                statement.executeUpdate();
            }
        }
    }

    private static void ensureOverallLimitTable(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS overall_monthly_budget ("
                + "id INT NOT NULL PRIMARY KEY, monthly_limit DECIMAL(10,2) NOT NULL)");
        }
    }

    private static String categoryKey(String category) {
        return category.toLowerCase(java.util.Locale.ROOT);
    }
}
