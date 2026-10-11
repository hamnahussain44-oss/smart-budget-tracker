<<<<<<< HEAD
# Smart Budget Tracker

A Java and Spring Boot web application for recording expenses and keeping track of monthly budgets. The dashboard reads and saves data in MySQL.

## Features

- Monthly spending and budget summaries
- Spending totals by category and budget progress
- Add, edit and delete expenses
- Add and remove category budgets, plus an overall monthly budget
- Recurring expenses
- Six-month spending chart and searchable expense history
- CSV export
- Selectable dashboard colour themes

## Built with

- Java 22
- Spring Boot and Thymeleaf
- MySQL and JDBC
- Maven

## Run locally

You’ll need Java 22, Maven and a running MySQL server.

1. Create the database and tables by running `database/schema.sql` with your MySQL client. For example:

   ```bash
   mysql -u YOUR_MYSQL_USER -p < database/schema.sql
   ```

2. Set the database connection values in the terminal where you’ll run the app. Replace the user and password with your own MySQL details:

   ```bash
   export BUDGET_DB_URL="jdbc:mysql://localhost:3306/budget_tracker"
   export BUDGET_DB_USER="YOUR_MYSQL_USER"
   export BUDGET_DB_PASSWORD="YOUR_MYSQL_PASSWORD"
   ```

   If your local MySQL account has no password, set `BUDGET_DB_PASSWORD=""`.

3. Start the application from the project folder:

   ```bash
   mvn spring-boot:run
   ```

4. Open [http://localhost:8080](http://localhost:8080).

The app may create supporting tables for recurring expenses and the overall budget automatically. Do not commit database passwords or other private credentials to GitHub; keep them in your local terminal environment.
=======
# smart-budget-tracker
Java and Spring Boot budget tracker with a MySQL database and interactive spending dashboard.
>>>>>>> 6c94063c39ca2491b6ef1c43ce523274eac81647
