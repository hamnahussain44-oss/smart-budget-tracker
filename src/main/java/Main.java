import com.example.budgettracker.Budget;
import com.example.budgettracker.BudgetRepository;
import com.example.budgettracker.Expense;
import com.example.budgettracker.ExpenseRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.sql.SQLException;

public class Main {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        try (ExpenseRepository expenseRepository = new ExpenseRepository()) {
            runTracker(scanner, expenseRepository, new BudgetRepository());
        } catch (SQLException | IllegalStateException e) {
            System.err.println("Could not use the budget database: " + e.getMessage());
            System.err.println("Check that MySQL is running and the database settings are set in this Terminal.");
        } finally {
            scanner.close();
        }
    }

    private static void runTracker(
        Scanner scanner,
        ExpenseRepository expenseRepository,
        BudgetRepository budgetRepository
    )
        throws SQLException {

        List<Expense> expenses = expenseRepository.findForMonth(YearMonth.now());

        System.out.println("=== SMART BUDGET TRACKER ===");
        System.out.println("Loaded " + expenses.size() + " saved expense(s) for this month.");

        // 1. Load saved monthly budgets, asking only for missing or changed limits.
        List<Budget> budgets = getOrConfigureBudgets(scanner, budgetRepository);
        Budget foodBudget = findBudget(budgets, "Food");
        Budget transportBudget = findBudget(budgets, "Transport");
        Budget shoppingBudget = findBudget(budgets, "Shopping");

        // 2. Ask how many new expenses to enter; allow zero to review saved data.
        int numberOfExpenses;

        while (true) {
            System.out.print("How many new expenses would you like to add? (0 to skip): ");

            try {
                numberOfExpenses =
                    Integer.parseInt(scanner.nextLine().trim());

                if (numberOfExpenses < 0) {
                    System.out.println("Enter zero or a positive whole number.");
                    continue;
                }

                break;

            } catch (NumberFormatException e) {
                System.out.println("Enter a whole number.");
            }
        }

        // 3. Collect expenses
        for (int i = 0; i < numberOfExpenses; i++) {

            System.out.println("\nExpense " + (i + 1));

            BigDecimal amount;

            while (true) {
                System.out.print("Enter amount: €");

                try {
                    amount = new BigDecimal(scanner.nextLine().trim());

                    if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                        System.out.println("Amount must be greater than zero.");
                        continue;
                    }

                    break;

                } catch (NumberFormatException e) {
                    System.out.println("Enter a valid amount, e.g. 8.50");
                }
            }

            String category;

            while (true) {
                System.out.print("Enter category (Food/Transport/Shopping): ");
                category = scanner.nextLine().trim();

                if (category.equalsIgnoreCase("Food")
                    || category.equalsIgnoreCase("Transport")
                    || category.equalsIgnoreCase("Shopping")) {
                    break;
                }

                System.out.println("Invalid category. Try again.");
            }

            System.out.print("Enter description: ");
            String description = scanner.nextLine().trim();

            while (description.isEmpty()) {
                System.out.print("Description cannot be empty. Enter description: ");
                description = scanner.nextLine().trim();
            }

            Expense expense = new Expense(
                amount, category, LocalDate.now(), description
            );
            expenseRepository.save(expense);
            expenses.add(expense);
            System.out.println("Saved to MySQL.");
        }

        // 4. Calculate spending in each category
        BigDecimal totalSpent = BigDecimal.ZERO;
        BigDecimal foodSpent = BigDecimal.ZERO;
        BigDecimal transportSpent = BigDecimal.ZERO;
        BigDecimal shoppingSpent = BigDecimal.ZERO;

        for (Expense expense : expenses) {

            BigDecimal amount = expense.getAmount();
            totalSpent = totalSpent.add(amount);

            if (expense.getCategory().equalsIgnoreCase("Food")) {
                foodSpent = foodSpent.add(amount);
            } else if (expense.getCategory().equalsIgnoreCase("Transport")) {
                transportSpent = transportSpent.add(amount);
            } else if (expense.getCategory().equalsIgnoreCase("Shopping")) {
                shoppingSpent = shoppingSpent.add(amount);
            }
        }

        // 5. Display summary
        System.out.println("\n=== SPENDING SUMMARY ===");
        System.out.println("Total spent: €" + totalSpent);

        showBudgetStatus(foodBudget, foodSpent);
        showBudgetStatus(transportBudget, transportSpent);
        showBudgetStatus(shoppingBudget, shoppingSpent);

    }

    private static List<Budget> getOrConfigureBudgets(
        Scanner scanner,
        BudgetRepository repository
    ) throws SQLException {
        List<String> categories = List.of("Food", "Transport", "Shopping");
        Map<String, Budget> savedBudgets = new LinkedHashMap<>(repository.findAll());

        boolean allBudgetsSaved = categories.stream()
            .allMatch(category -> savedBudgets.containsKey(categoryKey(category)));

        if (allBudgetsSaved) {
            System.out.println("Saved monthly budgets:");
            for (String category : categories) {
                Budget budget = savedBudgets.get(categoryKey(category));
                System.out.println(category + ": €" + budget.getLimit());
            }

            if (!askYesNo(scanner, "Would you like to change these budgets? (y/n): ")) {
                return categories.stream()
                    .map(category -> savedBudgets.get(categoryKey(category)))
                    .toList();
            }

            savedBudgets.clear();
        }

        List<Budget> budgets = new ArrayList<>();
        for (String category : categories) {
            Budget budget = savedBudgets.get(categoryKey(category));
            if (budget == null) {
                budget = new Budget(category, getBudget(scanner, category));
                repository.save(budget);
                System.out.println("Saved " + category + " budget to MySQL.");
            }
            budgets.add(budget);
        }
        return budgets;
    }

    private static Budget findBudget(List<Budget> budgets, String category) {
        return budgets.stream()
            .filter(budget -> budget.getCategory().equalsIgnoreCase(category))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("No budget saved for " + category));
    }

    private static String categoryKey(String category) {
        return category.toLowerCase(Locale.ROOT);
    }

    private static boolean askYesNo(Scanner scanner, String question) {
        while (true) {
            System.out.print(question);
            String answer = scanner.nextLine().trim();
            if (answer.equalsIgnoreCase("y") || answer.equalsIgnoreCase("yes")) {
                return true;
            }
            if (answer.equalsIgnoreCase("n") || answer.equalsIgnoreCase("no")) {
                return false;
            }
            System.out.println("Please enter y or n.");
        }
    }

    // Ask for and validate a category budget
    public static BigDecimal getBudget(Scanner scanner, String category) {

        while (true) {
            System.out.print("Enter your monthly " + category + " budget: €");

            try {
                BigDecimal limit = new BigDecimal(
                    scanner.nextLine().trim()
                );

                if (limit.compareTo(BigDecimal.ZERO) <= 0) {
                    System.out.println("Budget must be greater than zero.");
                    continue;
                }

                return limit;

            } catch (NumberFormatException e) {
                System.out.println("Enter a valid amount, e.g. 100.00");
            }
        }
    }

    // Display spending and alerts for one category
    public static void showBudgetStatus(Budget budget, BigDecimal spent) {

        BigDecimal limit = budget.getLimit();
        BigDecimal remaining = budget.getRemaining(spent);

        System.out.println("\n" + budget.getCategory());
        System.out.println("Budget: €" + limit);
        System.out.println("Spent: €" + spent);
        System.out.println("Remaining: €" + remaining);

        if (budget.isOverBudget(spent)) {
            System.out.println("ALERT: Over budget!");
        } else if (budget.isNearLimit(spent)) {
            System.out.println("WARNING: You've used at least 80% of this budget.");
        } else {
            System.out.println("You're within budget.");
        }
    }
}
