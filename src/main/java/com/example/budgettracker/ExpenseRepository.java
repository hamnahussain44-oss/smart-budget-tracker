package com.example.budgettracker;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.time.YearMonth;

/** Reads and writes expenses in the budget_tracker MySQL database. */
public class ExpenseRepository implements AutoCloseable {

    private final Connection connection;

    public ExpenseRepository() throws SQLException {
        connection = DatabaseConnection.open();
    }

    public List<Expense> findForMonth(YearMonth month) throws SQLException {
        String sql = "SELECT id, amount, category, expense_date, description "
            + "FROM expenses WHERE expense_date >= ? AND expense_date < ? "
            + "ORDER BY expense_date, id";

        List<Expense> expenses = new ArrayList<>();

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDate(1, Date.valueOf(month.atDay(1)));
            statement.setDate(2, Date.valueOf(month.plusMonths(1).atDay(1)));

            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    LocalDate date = results.getDate("expense_date").toLocalDate();
                    expenses.add(new Expense(
                        results.getInt("id"),
                        results.getBigDecimal("amount"),
                        results.getString("category"),
                        date,
                        results.getString("description")
                    ));
                }
            }
        }

        return expenses;
    }

    public List<Expense> findBetween(LocalDate startInclusive, LocalDate endExclusive) throws SQLException {
        String sql = "SELECT id, amount, category, expense_date, description "
            + "FROM expenses WHERE expense_date >= ? AND expense_date < ? "
            + "ORDER BY expense_date, id";
        List<Expense> expenses = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDate(1, Date.valueOf(startInclusive));
            statement.setDate(2, Date.valueOf(endExclusive));
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    expenses.add(new Expense(
                        results.getInt("id"), results.getBigDecimal("amount"),
                        results.getString("category"), results.getDate("expense_date").toLocalDate(),
                        results.getString("description")
                    ));
                }
            }
        }
        return expenses;
    }

    public void addMonthlyRecurring(Expense expense) throws SQLException {
        ensureRecurringTables();
        String sql = "INSERT INTO recurring_expenses (amount, category, description, start_date, day_of_month, active) "
            + "VALUES (?, ?, ?, ?, ?, 1)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBigDecimal(1, expense.getAmount());
            statement.setString(2, expense.getCategory());
            statement.setString(3, expense.getDescription());
            statement.setDate(4, Date.valueOf(expense.getDate()));
            statement.setInt(5, expense.getDate().getDayOfMonth());
            statement.executeUpdate();
        }
    }

    public void createRecurringExpensesForMonth(YearMonth month) throws SQLException {
        if (!month.equals(YearMonth.now())) return;
        ensureRecurringTables();
        String sql = "SELECT id, amount, category, description, start_date, day_of_month "
            + "FROM recurring_expenses WHERE active = 1 AND start_date < ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setDate(1, Date.valueOf(month.atDay(1)));
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    int recurringId = results.getInt("id");
                    int day = Math.min(results.getInt("day_of_month"), month.lengthOfMonth());
                    LocalDate date = month.atDay(day);
                    connection.setAutoCommit(false);
                    try {
                        int inserted;
                        try (PreparedStatement occurrence = connection.prepareStatement(
                                "INSERT IGNORE INTO recurring_expense_months (recurring_id, occurrence_month) VALUES (?, ?)")) {
                            occurrence.setInt(1, recurringId);
                            occurrence.setString(2, month.toString());
                            inserted = occurrence.executeUpdate();
                        }
                        if (inserted > 0) {
                            try (PreparedStatement expense = connection.prepareStatement(
                                    "INSERT INTO expenses (amount, category, expense_date, description) VALUES (?, ?, ?, ?)")) {
                                expense.setBigDecimal(1, results.getBigDecimal("amount"));
                                expense.setString(2, results.getString("category"));
                                expense.setDate(3, Date.valueOf(date));
                                expense.setString(4, results.getString("description"));
                                expense.executeUpdate();
                            }
                        }
                        connection.commit();
                    } catch (SQLException exception) {
                        connection.rollback();
                        throw exception;
                    } finally {
                        connection.setAutoCommit(true);
                    }
                }
            }
        }
    }

    public void deactivateRecurringForCategory(String category) throws SQLException {
        ensureRecurringTables();
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE recurring_expenses SET active = 0 WHERE LOWER(category) = LOWER(?)")) {
            statement.setString(1, category.trim());
            statement.executeUpdate();
        }
    }

    private void ensureRecurringTables() throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS recurring_expenses ("
                + "id INT NOT NULL AUTO_INCREMENT PRIMARY KEY, amount DECIMAL(10,2) NOT NULL, "
                + "category VARCHAR(40) NOT NULL, description VARCHAR(255) NOT NULL, "
                + "start_date DATE NOT NULL, day_of_month INT NOT NULL, active BOOLEAN NOT NULL DEFAULT TRUE)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS recurring_expense_months ("
                + "recurring_id INT NOT NULL, occurrence_month CHAR(7) NOT NULL, "
                + "PRIMARY KEY (recurring_id, occurrence_month), "
                + "FOREIGN KEY (recurring_id) REFERENCES recurring_expenses(id) ON DELETE CASCADE)");
        }
    }

    public void save(Expense expense) throws SQLException {
        String sql = "INSERT INTO expenses "
            + "(amount, category, expense_date, description) VALUES (?, ?, ?, ?)";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBigDecimal(1, expense.getAmount());
            statement.setString(2, expense.getCategory());
            statement.setDate(3, Date.valueOf(expense.getDate()));
            statement.setString(4, expense.getDescription());
            statement.executeUpdate();
        }
    }

    public void update(int id, Expense expense) throws SQLException {
        String sql = "UPDATE expenses SET amount = ?, category = ?, expense_date = ?, description = ? "
            + "WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBigDecimal(1, expense.getAmount());
            statement.setString(2, expense.getCategory());
            statement.setDate(3, Date.valueOf(expense.getDate()));
            statement.setString(4, expense.getDescription());
            statement.setInt(5, id);
            statement.executeUpdate();
        }
    }

    public int delete(int id) throws SQLException {
        String sql = "DELETE FROM expenses WHERE id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            return statement.executeUpdate();
        }
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }
}
