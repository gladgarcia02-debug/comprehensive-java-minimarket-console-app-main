package com.conveniencestore;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public class Database {
    private final String url;
    private final String username;
    private final String password;
    private final Properties configuration;

    public Database(String url, String username, String password) {
        this(url, username, password, EnvironmentConfig.load());
    }

    private Database(String url, String username, String password, Properties configuration) {
        this.url = url;
        this.username = username;
        this.password = password;
        this.configuration = configuration;
    }

    public static Database fromEnvironment() {
        Properties configuration = EnvironmentConfig.load();
        String url = requiredConfiguration(configuration, "DATABASE_URL");
        String username = requiredConfiguration(configuration, "DATABASE_USERNAME");
        String password = requiredConfiguration(configuration, "DATABASE_PASSWORD");
        return new Database(url, username, password, configuration);
    }

    private static String requiredConfiguration(Properties configuration, String name) {
        String value = EnvironmentConfig.get(configuration, name);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("Set " + name + " in the .env file or environment.");
        }
        return value;
    }

    public Connection connect() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }

    public void initialize() throws SQLException {
        runSchema();
        String adminUsername = EnvironmentConfig.get(configuration, "ADMIN_USERNAME");
        String adminPassword = EnvironmentConfig.get(configuration, "ADMIN_PASSWORD");
        if ((adminUsername == null) != (adminPassword == null)) {
            throw new IllegalStateException("Set both ADMIN_USERNAME and ADMIN_PASSWORD to configure an admin.");
        }
        if (adminUsername != null) {
            if (adminUsername.trim().isEmpty() || adminPassword.isEmpty()) {
                throw new IllegalStateException("ADMIN_USERNAME and ADMIN_PASSWORD must not be empty.");
            }
            try (Connection connection = connect();
                    PreparedStatement statement = connection.prepareStatement(
                            "INSERT INTO admins (username, password_hash) VALUES (?, ?) "
                                    + "ON CONFLICT (username) DO NOTHING")) {
                statement.setString(1, adminUsername);
                statement.setString(2, PasswordHasher.hash(adminPassword));
                statement.executeUpdate();
            }
        }
    }

    private void runSchema() throws SQLException {
        InputStream schema = Database.class.getResourceAsStream("/schema.sql");
        if (schema == null) {
            throw new SQLException("The database schema resource /schema.sql was not found.");
        }
        StringBuilder contents = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(schema, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                contents.append(line).append('\n');
            }
        } catch (IOException e) {
            throw new SQLException("Unable to read the database schema.", e);
        }

        try (Connection connection = connect(); Statement statement = connection.createStatement()) {
            for (String sql : contents.toString().split(";")) {
                if (!sql.trim().isEmpty()) {
                    statement.execute(sql);
                }
            }
        }
    }

    public boolean registerCustomer(String username, String password) throws SQLException {
        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO customers (username, password_hash) VALUES (?, ?)")) {
            statement.setString(1, username);
            statement.setString(2, PasswordHasher.hash(password));
            statement.executeUpdate();
            return true;
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) {
                return false;
            }
            throw e;
        }
    }

    public Customer authenticateCustomer(String username, String password) throws SQLException {
        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT password_hash, balance FROM customers WHERE username = ?")) {
            statement.setString(1, username);
            try (ResultSet results = statement.executeQuery()) {
                if (results.next() && PasswordHasher.verify(password, results.getString("password_hash"))) {
                    return new Customer(username, results.getDouble("balance"));
                }
            }
        }
        return null;
    }

    public boolean authenticateAdmin(String username, String password) throws SQLException {
        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT password_hash FROM admins WHERE username = ?")) {
            statement.setString(1, username);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() && PasswordHasher.verify(password, results.getString("password_hash"));
            }
        }
    }

    public boolean createAdmin(String username, String password) throws SQLException {
        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO admins (username, password_hash) VALUES (?, ?)")) {
            statement.setString(1, username);
            statement.setString(2, PasswordHasher.hash(password));
            statement.executeUpdate();
            return true;
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) return false;
            throw e;
        }
    }

    public void setCustomerBalance(String username, double balance) throws SQLException {
        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(
                        "UPDATE customers SET balance = ? WHERE username = ?")) {
            statement.setBigDecimal(1, BigDecimal.valueOf(balance));
            statement.setString(2, username);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Customer account was not found while updating its balance.");
            }
        }
    }

    public List<Customer> getCustomers() throws SQLException {
        List<Customer> customers = new ArrayList<>();
        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT username, balance FROM customers ORDER BY username");
                ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                customers.add(new Customer(results.getString("username"), results.getDouble("balance")));
            }
        }
        return customers;
    }

    public Product saveProduct(Product product) throws SQLException {
        try (Connection connection = connect();
                PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO products (name, price) VALUES (?, ?) "
                                + "ON CONFLICT (name) DO NOTHING");
                PreparedStatement select = connection.prepareStatement(
                        "SELECT product_id, name, price, quantity FROM products WHERE name = ?")) {
            insert.setString(1, product.getName());
            insert.setBigDecimal(2, BigDecimal.valueOf(product.getPrice()));
            insert.executeUpdate();

            select.setString(1, product.getName());
            try (ResultSet results = select.executeQuery()) {
                if (results.next()) {
                    return new Product(results.getLong("product_id"),
                            results.getString("name"), results.getDouble("price"),
                            results.getInt("quantity"));
                }
            }
        }
        throw new SQLException("Unable to load the saved product " + product.getName() + ".");
    }

    public Product createProduct(String name, BigDecimal price, int quantity) throws SQLException {
        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO products (name, price, quantity) VALUES (?, ?, ?) "
                                + "RETURNING product_id, name, price, quantity")) {
            statement.setString(1, name);
            statement.setBigDecimal(2, price);
            statement.setInt(3, quantity);
            try (ResultSet results = statement.executeQuery()) {
                if (results.next()) {
                    return new Product(results.getLong("product_id"),
                            results.getString("name"), results.getDouble("price"),
                            results.getInt("quantity"));
                }
            }
        }
        throw new SQLException("Unable to create the product " + name + ".");
    }

    public List<Product> getProducts() throws SQLException {
        List<Product> products = new ArrayList<>();
        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT product_id, name, price, quantity FROM products ORDER BY product_id");
                ResultSet results = statement.executeQuery()) {
            while (results.next()) {
                products.add(new Product(results.getLong("product_id"),
                        results.getString("name"), results.getDouble("price"),
                        results.getInt("quantity")));
            }
        }
        return products;
    }

    public boolean addCartItem(String username, Product product, int quantity) throws SQLException {
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                BigDecimal balance;
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT balance FROM customers WHERE username = ? FOR UPDATE")) {
                    statement.setString(1, username);
                    try (ResultSet results = statement.executeQuery()) {
                        if (!results.next()) {
                            throw new SQLException("Customer account was not found while adding a cart item.");
                        }
                        balance = results.getBigDecimal("balance");
                    }
                }

                BigDecimal price;
                int stock;
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT price, quantity FROM products WHERE product_id = ? FOR UPDATE")) {
                    statement.setLong(1, product.getProductId());
                    try (ResultSet results = statement.executeQuery()) {
                        if (!results.next()) throw new SQLException("Product was not found while adding a cart item.");
                        price = results.getBigDecimal("price");
                        stock = results.getInt("quantity");
                    }
                }
                int currentQuantity;
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT COALESCE(SUM(quantity), 0) FROM cart_items "
                                + "WHERE username = ? AND product_id = ? AND transaction_id IS NULL")) {
                    statement.setString(1, username);
                    statement.setLong(2, product.getProductId());
                    try (ResultSet results = statement.executeQuery()) {
                        results.next();
                        currentQuantity = results.getInt(1);
                    }
                }
                if (currentQuantity + quantity > stock) {
                    connection.rollback();
                    throw new SQLException("Requested quantity exceeds available stock.");
                }
                BigDecimal lineTotal = price.multiply(BigDecimal.valueOf(quantity));
                BigDecimal cartTotal;
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT COALESCE(SUM(line_total), 0) AS cart_total FROM cart_items "
                                + "WHERE username = ? AND transaction_id IS NULL")) {
                    statement.setString(1, username);
                    try (ResultSet results = statement.executeQuery()) {
                        results.next();
                        cartTotal = results.getBigDecimal("cart_total");
                    }
                }
                if (cartTotal.add(lineTotal).compareTo(balance) > 0) {
                    connection.rollback();
                    return false;
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO cart_items (username, product_id, quantity, line_total) "
                                + "VALUES (?, ?, ?, ?) "
                                + "ON CONFLICT (username, product_id) WHERE transaction_id IS NULL "
                                + "DO UPDATE SET quantity = cart_items.quantity + EXCLUDED.quantity, "
                                + "line_total = cart_items.line_total + EXCLUDED.line_total")) {
                    statement.setString(1, username);
                    statement.setLong(2, product.getProductId());
                    statement.setInt(3, quantity);
                    statement.setBigDecimal(4, lineTotal);
                    statement.executeUpdate();
                }
                connection.commit();
                return true;
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public List<CartLine> getCartItems(String username) throws SQLException {
        List<CartLine> items = new ArrayList<>();
        try (Connection connection = connect();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT p.product_id, p.name, ci.quantity, ci.line_total "
                                + "FROM cart_items ci JOIN products p ON p.product_id = ci.product_id "
                                + "WHERE ci.username = ? AND ci.transaction_id IS NULL "
                                + "ORDER BY ci.cart_item_id")) {
            statement.setString(1, username);
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    items.add(new CartLine(results.getLong("product_id"), results.getString("name"),
                            results.getInt("quantity"), results.getDouble("line_total")));
                }
            }
        }
        return items;
    }

    public void setCartQuantity(String username, long productId, int quantity) throws SQLException {
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                if (quantity == 0) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "DELETE FROM cart_items WHERE username = ? AND product_id = ? AND transaction_id IS NULL")) {
                        statement.setString(1, username);
                        statement.setLong(2, productId);
                        statement.executeUpdate();
                    }
                    connection.commit();
                    return;
                }
                BigDecimal balance;
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT balance FROM customers WHERE username = ? FOR UPDATE")) {
                    statement.setString(1, username);
                    try (ResultSet results = statement.executeQuery()) {
                        if (!results.next()) throw new SQLException("Customer account was not found while updating the cart.");
                        balance = results.getBigDecimal("balance");
                    }
                }
                BigDecimal price;
                int stock;
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT price, quantity FROM products WHERE product_id = ? FOR UPDATE")) {
                    statement.setLong(1, productId);
                    try (ResultSet results = statement.executeQuery()) {
                        if (!results.next()) throw new SQLException("Product was not found while updating the cart.");
                        price = results.getBigDecimal("price");
                        stock = results.getInt("quantity");
                    }
                }
                BigDecimal newLineTotal = price.multiply(BigDecimal.valueOf(quantity));
                BigDecimal otherItems;
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT COALESCE(SUM(line_total), 0) FROM cart_items WHERE username = ? "
                                + "AND transaction_id IS NULL AND product_id <> ?")) {
                    statement.setString(1, username);
                    statement.setLong(2, productId);
                    try (ResultSet results = statement.executeQuery()) { results.next(); otherItems = results.getBigDecimal(1); }
                }
                if (BigDecimal.valueOf(quantity).compareTo(BigDecimal.valueOf(stock)) > 0) {
                    connection.rollback();
                    throw new SQLException("Requested quantity exceeds available stock.");
                }
                if (otherItems.add(newLineTotal).compareTo(balance) > 0) {
                    connection.rollback();
                    throw new SQLException("Updated quantity would exceed the customer's available balance.");
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE cart_items SET quantity = ?, line_total = ? WHERE username = ? "
                                + "AND product_id = ? AND transaction_id IS NULL")) {
                    statement.setInt(1, quantity); statement.setBigDecimal(2, newLineTotal);
                    statement.setString(3, username); statement.setLong(4, productId);
                    if (statement.executeUpdate() != 1) throw new SQLException("Cart item was not found.");
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally { connection.setAutoCommit(true); }
        }
    }

    public CheckoutResult checkout(String username, PaymentMethod paymentMethod) throws SQLException {
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                double balance;
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT balance FROM customers WHERE username = ? FOR UPDATE")) {
                    statement.setString(1, username);
                    try (ResultSet results = statement.executeQuery()) {
                        if (!results.next()) {
                            throw new SQLException("Customer account was not found during checkout.");
                        }
                        balance = results.getDouble("balance");
                    }
                }

                List<CartLine> items = new ArrayList<>();
                BigDecimal total = BigDecimal.ZERO;
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT p.product_id, p.name, p.quantity AS available_quantity, ci.quantity, ci.line_total "
                                + "FROM cart_items ci JOIN products p ON p.product_id = ci.product_id "
                                + "WHERE ci.username = ? AND ci.transaction_id IS NULL "
                                + "ORDER BY ci.product_id FOR UPDATE OF ci, p")) {
                    statement.setString(1, username);
                    try (ResultSet results = statement.executeQuery()) {
                        while (results.next()) {
                            CartLine item = new CartLine(results.getLong("product_id"), results.getString("name"),
                                    results.getInt("quantity"), results.getDouble("line_total"));
                            items.add(item);
                            total = total.add(BigDecimal.valueOf(item.getLineTotal()));
                            if (results.getInt("available_quantity") < item.getQuantity()) {
                                connection.rollback();
                                return CheckoutResult.outOfStock();
                            }
                        }
                    }
                }

                if (items.isEmpty()) {
                    connection.rollback();
                    return CheckoutResult.emptyCart();
                }
                if (total.compareTo(BigDecimal.valueOf(balance)) > 0) {
                    connection.rollback();
                    return CheckoutResult.insufficientBalance();
                }

                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE products SET quantity = quantity - ? WHERE product_id = ? AND quantity >= ?")) {
                    for (CartLine item : items) {
                        statement.setInt(1, item.getQuantity());
                        statement.setLong(2, item.getProductId());
                        statement.setInt(3, item.getQuantity());
                        statement.addBatch();
                    }
                    for (int updated : statement.executeBatch()) {
                        if (updated != 1 && updated != java.sql.Statement.SUCCESS_NO_INFO) {
                            connection.rollback();
                            return CheckoutResult.outOfStock();
                        }
                    }
                }

                long transactionId;
                try (PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO transactions (username, payment_method, total_amount) "
                                + "VALUES (?, ?, ?) RETURNING transaction_id")) {
                    statement.setString(1, username);
                    statement.setString(2, paymentMethod.name());
                    statement.setBigDecimal(3, total);
                    try (ResultSet results = statement.executeQuery()) {
                        results.next();
                        transactionId = results.getLong("transaction_id");
                    }
                }
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE cart_items SET transaction_id = ? "
                                + "WHERE username = ? AND transaction_id IS NULL")) {
                    statement.setLong(1, transactionId);
                    statement.setString(2, username);
                    statement.executeUpdate();
                }
                BigDecimal remaining = BigDecimal.valueOf(balance).subtract(total);
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE customers SET balance = ? WHERE username = ?")) {
                    statement.setBigDecimal(1, remaining);
                    statement.setString(2, username);
                    statement.executeUpdate();
                }

                connection.commit();
                return CheckoutResult.success(items, total.doubleValue(), remaining.doubleValue());
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        }
    }

    public static final class CartLine {
        private final long productId;
        private final String name;
        private final int quantity;
        private final double lineTotal;

        CartLine(long productId, String name, int quantity, double lineTotal) {
            this.productId = productId;
            this.name = name;
            this.quantity = quantity;
            this.lineTotal = lineTotal;
        }

        public String getName() {
            return name;
        }

        public long getProductId() { return productId; }

        public int getQuantity() {
            return quantity;
        }

        public double getLineTotal() {
            return lineTotal;
        }
    }

    public static final class CheckoutResult {
        public enum Status {
            SUCCESS,
            EMPTY_CART,
            INSUFFICIENT_BALANCE,
            OUT_OF_STOCK
        }

        private final Status status;
        private final List<CartLine> items;
        private final double total;
        private final double remainingBalance;

        private CheckoutResult(Status status, List<CartLine> items, double total, double remainingBalance) {
            this.status = status;
            this.items = items;
            this.total = total;
            this.remainingBalance = remainingBalance;
        }

        static CheckoutResult success(List<CartLine> items, double total, double remainingBalance) {
            return new CheckoutResult(Status.SUCCESS, items, total, remainingBalance);
        }

        static CheckoutResult emptyCart() {
            return new CheckoutResult(Status.EMPTY_CART, new ArrayList<CartLine>(), 0, 0);
        }

        static CheckoutResult insufficientBalance() {
            return new CheckoutResult(Status.INSUFFICIENT_BALANCE, new ArrayList<CartLine>(), 0, 0);
        }

        static CheckoutResult outOfStock() {
            return new CheckoutResult(Status.OUT_OF_STOCK, new ArrayList<CartLine>(), 0, 0);
        }

        public Status getStatus() {
            return status;
        }

        public List<CartLine> getItems() {
            return items;
        }

        public double getTotal() {
            return total;
        }

        public double getRemainingBalance() {
            return remainingBalance;
        }
    }
}
