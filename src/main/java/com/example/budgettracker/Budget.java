package com.example.budgettracker;

import java.math.BigDecimal;

public class Budget {

    private String category;
    private BigDecimal limit;

    public Budget(String category, BigDecimal limit) {
        this.category = category;
        this.limit = limit;
    }

    public String getCategory() {
        return category;
    }

    public BigDecimal getLimit() {
        return limit;
    }

    public BigDecimal getRemaining(BigDecimal spent) {
        return limit.subtract(spent);
    }

    public boolean isOverBudget(BigDecimal spent) {
        return spent.compareTo(limit) > 0;
    }

    public boolean isNearLimit(BigDecimal spent) {
        BigDecimal warningLevel =
            limit.multiply(new BigDecimal("0.80"));

        return spent.compareTo(warningLevel) >= 0
            && !isOverBudget(spent);
    }
}
