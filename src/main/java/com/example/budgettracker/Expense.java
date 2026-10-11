package com.example.budgettracker;

import java.math.BigDecimal;
import java.time.LocalDate;

public class Expense {
    private int id;
    private BigDecimal amount;
    private String category;
    private LocalDate date;
    private String description;

    public Expense(BigDecimal amount, String category,
                   LocalDate date, String description) {
        this(0, amount, category, date, description);
    }

    public Expense(int id, BigDecimal amount, String category,
                   LocalDate date, String description) {
        this.id = id;
        this.amount = amount;
        this.category = category;
        this.date = date;
        this.description = description;
    }

    public int getId() {
        return id;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCategory() {
        return category;
    }

    public LocalDate getDate() {
        return date;
    }

    public String getDescription() {
        return description;
    }
}
