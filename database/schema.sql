CREATE DATABASE IF NOT EXISTS budget_tracker;
USE budget_tracker;

CREATE TABLE IF NOT EXISTS expenses (
    id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    amount DECIMAL(10, 2) NOT NULL,
    category VARCHAR(100) NOT NULL,
    expense_date DATE NOT NULL,
    description VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS budgets (
    category VARCHAR(100) NOT NULL PRIMARY KEY,
    monthly_limit DECIMAL(10, 2) NOT NULL
);
