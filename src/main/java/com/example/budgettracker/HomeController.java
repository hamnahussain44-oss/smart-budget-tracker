package com.example.budgettracker;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.HashSet;
import java.math.RoundingMode;

import org.springframework.ui.Model;
import org.springframework.stereotype.Controller;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ContentDisposition;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class HomeController {

    @GetMapping({"/", "/dashboard"})
    public String home(@RequestParam(required = false) String month, Model model) {
        YearMonth selectedMonth = YearMonth.now();
        if (month != null && !month.isBlank()) {
            try {
                selectedMonth = YearMonth.parse(month);
            } catch (java.time.format.DateTimeParseException exception) {
                model.addAttribute("monthError", "Choose a valid month.");
            }
        }

        try (ExpenseRepository expenseRepository = new ExpenseRepository()) {
            expenseRepository.createRecurringExpensesForMonth(selectedMonth);
            var expenses = expenseRepository.findForMonth(selectedMonth);
            var previousMonthExpenses = expenseRepository.findForMonth(selectedMonth.minusMonths(1));
            expenses.sort(Comparator.comparing(Expense::getDate).reversed());
            Map<String, Budget> budgets = new BudgetRepository().findAll();
            List<TrendCategory> trendCategories = new ArrayList<>();
            Map<String, TrendCategory> trendCategoryByKey = new LinkedHashMap<>();
            String[] chartColors = {"chart-color-0", "chart-color-1", "chart-color-2", "chart-color-3", "chart-color-4", "chart-color-5"};
            for (Budget budget : budgets.values()) {
                String key = budget.getCategory().toLowerCase(java.util.Locale.ROOT);
                TrendCategory category = new TrendCategory(budget.getCategory(), chartColors[trendCategoryByKey.size() % chartColors.length]);
                trendCategoryByKey.put(key, category);
                trendCategories.add(category);
            }
            List<TrendMonth> trendMonths = new ArrayList<>();
            YearMonth trendStart = selectedMonth.minusMonths(5);
            var trendExpenses = expenseRepository.findBetween(trendStart.atDay(1), selectedMonth.plusMonths(1).atDay(1));
            for (Expense trendExpense : trendExpenses) {
                String key = trendExpense.getCategory().toLowerCase(java.util.Locale.ROOT);
                if (!trendCategoryByKey.containsKey(key)) {
                    TrendCategory category = new TrendCategory(trendExpense.getCategory(), chartColors[trendCategoryByKey.size() % chartColors.length]);
                    trendCategoryByKey.put(key, category);
                    trendCategories.add(category);
                }
            }
            Map<YearMonth, Map<String, BigDecimal>> monthlyCategoryTotals = new LinkedHashMap<>();
            BigDecimal maxTrendTotal = BigDecimal.ZERO;
            for (int offset = 0; offset < 6; offset++) {
                monthlyCategoryTotals.put(trendStart.plusMonths(offset), new LinkedHashMap<>());
            }
            for (Expense trendExpense : trendExpenses) {
                YearMonth expenseMonth = YearMonth.from(trendExpense.getDate());
                monthlyCategoryTotals.get(expenseMonth).merge(
                    trendExpense.getCategory().toLowerCase(java.util.Locale.ROOT), trendExpense.getAmount(), BigDecimal::add
                );
            }
            for (var entry : monthlyCategoryTotals.entrySet()) {
                BigDecimal monthTotal = entry.getValue().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                if (monthTotal.compareTo(maxTrendTotal) > 0) maxTrendTotal = monthTotal;
            }
            for (var entry : monthlyCategoryTotals.entrySet()) {
                BigDecimal monthTotal = entry.getValue().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                List<TrendSegment> segments = new ArrayList<>();
                for (TrendCategory category : trendCategories) {
                    BigDecimal amount = entry.getValue().getOrDefault(category.getName().toLowerCase(java.util.Locale.ROOT), BigDecimal.ZERO);
                    if (amount.signum() > 0 && maxTrendTotal.signum() > 0) {
                        double width = amount.multiply(new BigDecimal("100"))
                            .divide(maxTrendTotal, 4, RoundingMode.HALF_UP).doubleValue();
                        segments.add(new TrendSegment(category.getName(), amount, width, category.getColorClass()));
                    }
                }
                trendMonths.add(new TrendMonth(entry.getKey().toString(), monthTotal, segments));
            }
            Map<String, BigDecimal> spendingByCategory = new HashMap<>();
            Map<String, String> statusByCategory = new HashMap<>();
            Map<String, String> statusClassByCategory = new HashMap<>();
            Map<String, Integer> percentUsedByCategory = new HashMap<>();
            Map<String, Integer> barWidthByCategory = new HashMap<>();
            BigDecimal totalSpent = BigDecimal.ZERO;
            BigDecimal previousTotalSpent = BigDecimal.ZERO;
            BigDecimal totalBudget = BigDecimal.ZERO;

            for (Expense expense : expenses) {
                String key = expense.getCategory().toLowerCase(java.util.Locale.ROOT);
                spendingByCategory.merge(key, expense.getAmount(), BigDecimal::add);
                totalSpent = totalSpent.add(expense.getAmount());
            }
            for (Expense expense : previousMonthExpenses) previousTotalSpent = previousTotalSpent.add(expense.getAmount());
            BigDecimal monthDifference = totalSpent.subtract(previousTotalSpent);
            String monthComparison;
            if (previousTotalSpent.signum() == 0) {
                monthComparison = totalSpent.signum() == 0 ? "No spending this month or last month." : "No spending last month to compare.";
            } else {
                BigDecimal percentChange = monthDifference.abs().multiply(new BigDecimal("100"))
                    .divide(previousTotalSpent, 1, RoundingMode.HALF_UP);
                monthComparison = (monthDifference.signum() > 0 ? "Up " : monthDifference.signum() < 0 ? "Down " : "Unchanged ")
                    + percentChange + "% (€" + monthDifference.abs().setScale(2, RoundingMode.HALF_UP) + ") compared with last month";
            }
            List<BudgetAlert> budgetAlerts = new ArrayList<>();
            for (Budget budget : budgets.values()) {
                totalBudget = totalBudget.add(budget.getLimit());
                String key = budget.getCategory().toLowerCase(java.util.Locale.ROOT);
                BigDecimal spent = spendingByCategory.getOrDefault(key, BigDecimal.ZERO);
                int percentUsed = spent.multiply(new BigDecimal("100"))
                    .divide(budget.getLimit(), 0, RoundingMode.HALF_UP)
                    .intValue();
                percentUsedByCategory.put(key, percentUsed);
                barWidthByCategory.put(key, Math.min(percentUsed, 100));
                if (budget.isOverBudget(spent)) {
                    statusByCategory.put(key, "Over budget by €" + budget.getRemaining(spent).abs());
                    statusClassByCategory.put(key, "over-budget");
                    budgetAlerts.add(new BudgetAlert(budget.getCategory(), statusByCategory.get(key), "over-budget"));
                } else if (budget.isNearLimit(spent)) {
                    statusByCategory.put(key, "Near limit — €" + budget.getRemaining(spent) + " left");
                    statusClassByCategory.put(key, "near-limit");
                    budgetAlerts.add(new BudgetAlert(budget.getCategory(), statusByCategory.get(key), "near-limit"));
                } else {
                    statusByCategory.put(key, "€" + budget.getRemaining(spent) + " left");
                    statusClassByCategory.put(key, "within-budget");
                }
            }

            BigDecimal overallMonthlyLimit = new BudgetRepository().findOverallLimit();
            int overallPercentUsed = 0;
            int overallBarWidth = 0;
            String overallLimitStatus = "Set an overall limit to track your total monthly spending.";
            String overallLimitClass = "within-budget";
            if (overallMonthlyLimit != null) {
                overallPercentUsed = totalSpent.multiply(new BigDecimal("100"))
                    .divide(overallMonthlyLimit, 0, RoundingMode.HALF_UP).intValue();
                overallBarWidth = Math.min(overallPercentUsed, 100);
                BigDecimal overallRemaining = overallMonthlyLimit.subtract(totalSpent);
                if (overallRemaining.signum() < 0) {
                    overallLimitStatus = "Over your overall limit by €" + overallRemaining.abs().setScale(2, RoundingMode.HALF_UP);
                    overallLimitClass = "over-budget";
                } else if (overallPercentUsed >= 80) {
                    overallLimitStatus = "Nearly at your overall limit — €" + overallRemaining.setScale(2, RoundingMode.HALF_UP) + " left";
                    overallLimitClass = "near-limit";
                } else {
                    overallLimitStatus = "€" + overallRemaining.setScale(2, RoundingMode.HALF_UP) + " left this month";
                }
            }

            model.addAttribute("month", selectedMonth);
            model.addAttribute("monthValue", selectedMonth.toString());
            model.addAttribute("budgets", budgets.values());
            model.addAttribute("budgetCategoryNames", new HashSet<>(budgets.values().stream().map(Budget::getCategory).toList()));
            model.addAttribute("spendingByCategory", spendingByCategory);
            model.addAttribute("statusByCategory", statusByCategory);
            model.addAttribute("statusClassByCategory", statusClassByCategory);
            model.addAttribute("percentUsedByCategory", percentUsedByCategory);
            model.addAttribute("barWidthByCategory", barWidthByCategory);
            model.addAttribute("totalSpent", totalSpent);
            model.addAttribute("totalBudget", totalBudget);
            model.addAttribute("remaining", totalBudget.subtract(totalSpent));
            LocalDate defaultExpenseDate = selectedMonth.equals(YearMonth.now())
                ? LocalDate.now() : selectedMonth.atDay(1);
            model.addAttribute("today", defaultExpenseDate);
            model.addAttribute("expenses", expenses);
            model.addAttribute("trendMonths", trendMonths);
            model.addAttribute("trendCategories", trendCategories);
            model.addAttribute("hasTrendSpending", maxTrendTotal.signum() > 0);
            model.addAttribute("monthComparison", monthComparison);
            model.addAttribute("monthComparisonClass", monthDifference.signum() > 0 ? "over-budget" : "within-budget");
            model.addAttribute("budgetAlerts", budgetAlerts);
            model.addAttribute("overallMonthlyLimit", overallMonthlyLimit);
            model.addAttribute("overallPercentUsed", overallPercentUsed);
            model.addAttribute("overallBarWidth", overallBarWidth);
            model.addAttribute("overallLimitStatus", overallLimitStatus);
            model.addAttribute("overallLimitClass", overallLimitClass);
        } catch (SQLException | IllegalStateException exception) {
            model.addAttribute("databaseError", exception.getMessage());
        }
        return "dashboard";
    }

    @PostMapping("/expenses")
    public String addExpense(
        @RequestParam String amount,
        @RequestParam String category,
        @RequestParam String date,
        @RequestParam String description,
        @RequestParam(required = false, defaultValue = "false") boolean repeatMonthly,
        RedirectAttributes redirectAttributes
    ) {
        try {
            BigDecimal parsedAmount = new BigDecimal(amount);
            if (parsedAmount.compareTo(BigDecimal.ZERO) <= 0) {
                redirectAttributes.addFlashAttribute("formError", "Enter an amount greater than zero.");
                return "redirect:/dashboard";
            }

            Map<String, Budget> budgets = new BudgetRepository().findAll();
            Budget selectedBudget = budgets.values().stream()
                .filter(budget -> budget.getCategory().equalsIgnoreCase(category))
                .findFirst()
                .orElse(null);
            if (selectedBudget == null) {
                redirectAttributes.addFlashAttribute("formError", "Choose one of the saved budget categories.");
                return "redirect:/dashboard";
            }
            if (description.isBlank()) {
                redirectAttributes.addFlashAttribute("formError", "Please enter a description.");
                return "redirect:/dashboard";
            }

            LocalDate expenseDate = LocalDate.parse(date);
            Expense expense = new Expense(
                parsedAmount,
                selectedBudget.getCategory(),
                expenseDate,
                description.trim()
            );
            try (ExpenseRepository expenseRepository = new ExpenseRepository()) {
                expenseRepository.save(expense);
                if (repeatMonthly) expenseRepository.addMonthlyRecurring(expense);
            }
            redirectAttributes.addAttribute("month", YearMonth.from(expenseDate).toString());
            redirectAttributes.addFlashAttribute("formSuccess", repeatMonthly
                ? "Expense saved and set to repeat monthly from the month after its date."
                : "Expense saved. Your dashboard has been updated.");
        } catch (NumberFormatException exception) {
            redirectAttributes.addFlashAttribute("formError", "Enter a valid amount, for example 8.50.");
        } catch (java.time.format.DateTimeParseException exception) {
            redirectAttributes.addFlashAttribute("formError", "Choose a valid date.");
        } catch (SQLException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("formError", "Could not save the expense: " + exception.getMessage());
        }
        return "redirect:/dashboard";
    }

    @PostMapping("/expenses/update")
    public String updateExpense(
        @RequestParam int id,
        @RequestParam String amount,
        @RequestParam String category,
        @RequestParam String date,
        @RequestParam String description,
        RedirectAttributes redirectAttributes
    ) {
        try {
            BigDecimal parsedAmount = new BigDecimal(amount);
            LocalDate expenseDate = LocalDate.parse(date);
            if (id <= 0 || parsedAmount.compareTo(BigDecimal.ZERO) <= 0 || description.isBlank()) {
                redirectAttributes.addFlashAttribute("editError", "Check the amount and description, then try again.");
                return "redirect:/dashboard";
            }

            Map<String, Budget> budgets = new BudgetRepository().findAll();
            Budget selectedBudget = budgets.values().stream()
                .filter(budget -> budget.getCategory().equalsIgnoreCase(category))
                .findFirst()
                .orElse(null);
            if (selectedBudget == null) {
                redirectAttributes.addFlashAttribute("editError", "Choose one of the saved budget categories.");
                return "redirect:/dashboard";
            }

            Expense expense = new Expense(
                id, parsedAmount, selectedBudget.getCategory(), expenseDate, description.trim()
            );
            try (ExpenseRepository expenseRepository = new ExpenseRepository()) {
                expenseRepository.update(id, expense);
            }
            redirectAttributes.addAttribute("month", YearMonth.from(expenseDate).toString());
            redirectAttributes.addFlashAttribute("editSuccess", "Expense updated.");
        } catch (NumberFormatException exception) {
            redirectAttributes.addFlashAttribute("editError", "Enter a valid amount, for example 8.50.");
        } catch (java.time.format.DateTimeParseException exception) {
            redirectAttributes.addFlashAttribute("editError", "Choose a valid date.");
        } catch (SQLException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("editError", "Could not update the expense: " + exception.getMessage());
        }
        return "redirect:/dashboard";
    }

    @PostMapping("/expenses/delete")
    public String deleteExpense(
        @RequestParam int id,
        @RequestParam(required = false) String month,
        RedirectAttributes redirectAttributes
    ) {
        String redirectMonth = month;
        try {
            if (id <= 0) {
                redirectAttributes.addFlashAttribute("deleteError", "That expense could not be identified.");
                return redirectToMonth(redirectAttributes, redirectMonth);
            }
            int deleted;
            try (ExpenseRepository expenseRepository = new ExpenseRepository()) {
                deleted = expenseRepository.delete(id);
            }
            if (deleted == 0) {
                redirectAttributes.addFlashAttribute("deleteError", "That expense was not found.");
            } else {
                redirectAttributes.addFlashAttribute("deleteSuccess", "Expense deleted.");
            }
        } catch (SQLException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("deleteError", "Could not delete the expense: " + exception.getMessage());
        }
        return redirectToMonth(redirectAttributes, redirectMonth);
    }

    private String redirectToMonth(RedirectAttributes redirectAttributes, String month) {
        try {
            if (month != null && !month.isBlank()) {
                YearMonth.parse(month);
                redirectAttributes.addAttribute("month", month);
            }
        } catch (java.time.format.DateTimeParseException ignored) {
            // Return to the current month if the supplied month is invalid.
        }
        return "redirect:/dashboard";
    }

    @PostMapping("/budgets")
    public String updateBudget(
        @RequestParam String category,
        @RequestParam String limit,
        RedirectAttributes redirectAttributes
    ) {
        try {
            BigDecimal parsedLimit = new BigDecimal(limit);
            if (parsedLimit.compareTo(BigDecimal.ZERO) <= 0) {
                redirectAttributes.addFlashAttribute("budgetError", "Enter a budget greater than zero.");
                return "redirect:/dashboard";
            }

            Map<String, Budget> budgets = new BudgetRepository().findAll();
            Budget selectedBudget = budgets.values().stream()
                .filter(budget -> budget.getCategory().equalsIgnoreCase(category))
                .findFirst()
                .orElse(null);
            if (selectedBudget == null) {
                redirectAttributes.addFlashAttribute("budgetError", "Choose one of the saved budget categories.");
                return "redirect:/dashboard";
            }

            new BudgetRepository().save(new Budget(selectedBudget.getCategory(), parsedLimit));
            redirectAttributes.addFlashAttribute("budgetSuccess", selectedBudget.getCategory() + " budget updated.");
        } catch (NumberFormatException exception) {
            redirectAttributes.addFlashAttribute("budgetError", "Enter a valid budget amount, for example 200.00.");
        } catch (SQLException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("budgetError", "Could not update the budget: " + exception.getMessage());
        }
        return "redirect:/dashboard";
    }

    @PostMapping("/budgets/delete")
    public String deleteBudget(@RequestParam String category, RedirectAttributes redirectAttributes) {
        try {
            Map<String, Budget> budgets = new BudgetRepository().findAll();
            Budget selected = budgets.values().stream()
                .filter(budget -> budget.getCategory().equalsIgnoreCase(category.trim()))
                .findFirst().orElse(null);
            if (selected == null) {
                redirectAttributes.addFlashAttribute("budgetError", "That budget category was not found.");
                return "redirect:/dashboard";
            }
            try (ExpenseRepository expenseRepository = new ExpenseRepository()) {
                expenseRepository.deactivateRecurringForCategory(selected.getCategory());
            }
            new BudgetRepository().delete(selected.getCategory());
            redirectAttributes.addFlashAttribute("budgetSuccess",
                selected.getCategory() + " budget deleted. Past expenses are still saved in your history.");
        } catch (SQLException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("budgetError", "Could not delete the budget: " + exception.getMessage());
        }
        return "redirect:/dashboard";
    }

    @PostMapping("/budgets/overall")
    public String saveOverallBudget(@RequestParam String limit, RedirectAttributes redirectAttributes) {
        try {
            BigDecimal parsedLimit = new BigDecimal(limit);
            if (parsedLimit.compareTo(BigDecimal.ZERO) <= 0) {
                redirectAttributes.addFlashAttribute("overallBudgetError", "Enter an overall limit greater than zero.");
            } else {
                new BudgetRepository().saveOverallLimit(parsedLimit);
                redirectAttributes.addFlashAttribute("overallBudgetSuccess", "Overall monthly limit saved.");
            }
        } catch (NumberFormatException exception) {
            redirectAttributes.addFlashAttribute("overallBudgetError", "Enter a valid amount, for example 1000.00.");
        } catch (SQLException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("overallBudgetError", "Could not save the overall limit: " + exception.getMessage());
        }
        return "redirect:/dashboard";
    }

    @PostMapping("/budgets/new")
    public String addBudgetCategory(
        @RequestParam String category,
        @RequestParam String limit,
        RedirectAttributes redirectAttributes
    ) {
        String cleanCategory = category.trim();
        try {
            if (cleanCategory.isBlank() || cleanCategory.length() > 40) {
                redirectAttributes.addFlashAttribute("newBudgetError", "Enter a category name up to 40 characters.");
                return "redirect:/dashboard";
            }
            BigDecimal parsedLimit = new BigDecimal(limit);
            if (parsedLimit.compareTo(BigDecimal.ZERO) <= 0) {
                redirectAttributes.addFlashAttribute("newBudgetError", "Enter a budget greater than zero.");
                return "redirect:/dashboard";
            }

            BudgetRepository repository = new BudgetRepository();
            Map<String, Budget> budgets = repository.findAll();
            String categoryKey = cleanCategory.toLowerCase(java.util.Locale.ROOT);
            if (budgets.containsKey(categoryKey)) {
                redirectAttributes.addFlashAttribute("newBudgetError", "That category already exists. Update its limit above.");
                return "redirect:/dashboard";
            }

            repository.save(new Budget(cleanCategory, parsedLimit));
            redirectAttributes.addFlashAttribute("newBudgetSuccess", cleanCategory + " budget added.");
        } catch (NumberFormatException exception) {
            redirectAttributes.addFlashAttribute("newBudgetError", "Enter a valid budget amount, for example 50.00.");
        } catch (SQLException | IllegalStateException exception) {
            redirectAttributes.addFlashAttribute("newBudgetError", "Could not add the budget: " + exception.getMessage());
        }
        return "redirect:/dashboard";
    }

    @GetMapping("/expenses/export")
    public ResponseEntity<byte[]> exportExpenses(@RequestParam(required = false) String month) {
        YearMonth selectedMonth = YearMonth.now();
        try {
            if (month != null && !month.isBlank()) {
                selectedMonth = YearMonth.parse(month);
            }
        } catch (java.time.format.DateTimeParseException exception) {
            return ResponseEntity.badRequest()
                .contentType(MediaType.TEXT_PLAIN)
                .body("Please choose a valid month.".getBytes(StandardCharsets.UTF_8));
        }

        try (ExpenseRepository expenseRepository = new ExpenseRepository()) {
            var expenses = expenseRepository.findForMonth(selectedMonth);
            StringBuilder csv = new StringBuilder("Date,Description,Category,Amount\r\n");
            for (Expense expense : expenses) {
                csv.append(expense.getDate()).append(',')
                    .append(csvCell(expense.getDescription())).append(',')
                    .append(csvCell(expense.getCategory())).append(',')
                    .append(expense.getAmount().toPlainString()).append("\r\n");
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
            headers.setContentDisposition(ContentDisposition.attachment()
                .filename("expenses-" + selectedMonth + ".csv")
                .build());
            return ResponseEntity.ok().headers(headers).body(csv.toString().getBytes(StandardCharsets.UTF_8));
        } catch (SQLException | IllegalStateException exception) {
            return ResponseEntity.internalServerError()
                .contentType(MediaType.TEXT_PLAIN)
                .body(("Could not export expenses: " + exception.getMessage()).getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String csvCell(String value) {
        String safeValue = value == null ? "" : value;
        return "\"" + safeValue.replace("\"", "\"\"") + "\"";
    }

    public static class TrendCategory {
        private final String name;
        private final String colorClass;
        TrendCategory(String name, String colorClass) { this.name = name; this.colorClass = colorClass; }
        public String getName() { return name; }
        public String getColorClass() { return colorClass; }
    }

    public static class TrendSegment {
        private final String name;
        private final BigDecimal amount;
        private final double width;
        private final String colorClass;
        TrendSegment(String name, BigDecimal amount, double width, String colorClass) {
            this.name = name; this.amount = amount; this.width = width; this.colorClass = colorClass;
        }
        public String getName() { return name; }
        public BigDecimal getAmount() { return amount; }
        public double getWidth() { return width; }
        public String getColorClass() { return colorClass; }
    }

    public static class TrendMonth {
        private final String label;
        private final BigDecimal total;
        private final List<TrendSegment> segments;
        TrendMonth(String label, BigDecimal total, List<TrendSegment> segments) {
            this.label = label; this.total = total; this.segments = segments;
        }
        public String getLabel() { return label; }
        public BigDecimal getTotal() { return total; }
        public List<TrendSegment> getSegments() { return segments; }
    }

    public static class BudgetAlert {
        private final String category;
        private final String message;
        private final String styleClass;
        BudgetAlert(String category, String message, String styleClass) {
            this.category = category; this.message = message; this.styleClass = styleClass;
        }
        public String getCategory() { return category; }
        public String getMessage() { return message; }
        public String getStyleClass() { return styleClass; }
    }
}
