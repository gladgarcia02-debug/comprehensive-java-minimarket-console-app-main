package com.conveniencestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.sql.SQLException;

public class Store {
    private List<Product> products;
    private Map<Customer, Map<Product, Integer>> shoppingCart;
    private final Database database;

    public Store() {
        this.database = null;
        products = new ArrayList<>();
        shoppingCart = new HashMap<>();
    }

    public Store(Database database) throws SQLException {
        this.database = database;
        products = new ArrayList<>();
        shoppingCart = new HashMap<>();
        if (database != null) {
            products.addAll(database.getProducts());
        }
    }

    public void addProduct(Product product) throws SQLException {
        Product storedProduct = database == null ? product : database.saveProduct(product);
        for (Product existingProduct : products) {
            if (existingProduct.getName().equals(storedProduct.getName())) {
                return;
            }
        }
        products.add(storedProduct);
    }

    public boolean isProductCatalogEmpty() throws SQLException {
        if (database != null) {
            products.clear();
            products.addAll(database.getProducts());
        }
        return products.isEmpty();
    }

    public void displayProducts() throws SQLException {
        if (database != null) {
            products.clear();
            products.addAll(database.getProducts());
        }
        System.out.println("Products available:");
        for (int i = 0; i < products.size(); i++) {
            Product product = products.get(i);
            System.out.println((i + 1) + ". " + product.getName() + " (Price: PHP " + product.getPrice() + ")");
        }
    }

    public boolean addToCart(Customer customer, int productNumber, int quantity) throws SQLException {
        if (productNumber < 1 || productNumber > products.size()) {
            System.out.println("Invalid product number. Item was not added.");
            return false;
        }
        if (quantity <= 0) {
            System.out.println("Quantity must be greater than zero. Item was not added.");
            return false;
        }

        Product product = products.get(productNumber - 1);
        double cartTotal = getCartTotal(customer);
        double itemTotal = product.getPrice() * quantity;
        if (cartTotal + itemTotal > customer.getBalance()) {
            System.out.println("Insufficient balance. Item was not added.");
            return false;
        } else {
            if (database != null) {
                if (!database.addCartItem(customer.getUsername(), product, quantity)) {
                    System.out.println("Insufficient balance. Item was not added.");
                    return false;
                }
                System.out.println("Item added to cart.");
                return true;
            }
            Map<Product, Integer> customerCart = shoppingCart.get(customer);
            if (customerCart == null) {
                customerCart = new HashMap<>();
                shoppingCart.put(customer, customerCart);
            }
            customerCart.put(product, customerCart.getOrDefault(product, 0) + quantity);
            System.out.println("Item added to cart.");
            return true;
        }
    }

    public void displayShoppingCart(Customer customer) throws SQLException {
        if (database != null) {
            List<Database.CartLine> items = database.getCartItems(customer.getUsername());
            System.out.println("Shopping cart for " + customer.getUsername() + ":");
            if (items.isEmpty()) {
                System.out.println("Your shopping cart is empty.");
                return;
            }
            for (Database.CartLine item : items) {
                System.out.println(item.getName() + " x " + item.getQuantity()
                        + " = PHP " + item.getLineTotal());
            }
            System.out.println("Cart total: PHP " + getCartTotal(customer));
            return;
        }
        Map<Product, Integer> customerCart = shoppingCart.get(customer);
        System.out.println("Shopping cart for " + customer.getUsername() + ":");
        if (customerCart == null || customerCart.isEmpty()) {
            System.out.println("Your shopping cart is empty.");
            return;
        }
        for (Map.Entry<Product, Integer> item : customerCart.entrySet()) {
            double itemTotal = item.getKey().getPrice() * item.getValue();
            System.out.println(item.getKey().getName() + " x " + item.getValue() + " = PHP " + itemTotal);
        }
        System.out.println("Cart total: PHP " + getCartTotal(customer));
    }

    public boolean checkout(Customer customer, PaymentMethod paymentMethod) throws SQLException {
        if (paymentMethod == null) {
            System.out.println("Invalid payment method. Checkout failed.");
            return false;
        }

        if (database != null) {
            Database.CheckoutResult result = database.checkout(customer.getUsername(), paymentMethod);
            if (result.getStatus() == Database.CheckoutResult.Status.EMPTY_CART) {
                System.out.println("Your shopping cart is empty. Nothing to check out.");
                return false;
            }
            if (result.getStatus() == Database.CheckoutResult.Status.INSUFFICIENT_BALANCE) {
                System.out.println("Insufficient balance. Checkout failed.");
                return false;
            }

            System.out.println("\nDemo receipt for " + customer.getUsername() + ":");
            System.out.println("Selected payment method: " + paymentMethod.getDisplayName());
            System.out.println("Demo checkout only. No real payment was processed.");
            for (Database.CartLine item : result.getItems()) {
                System.out.println(item.getName() + " x " + item.getQuantity()
                        + " = PHP " + item.getLineTotal());
            }
            customer.setBalance(result.getRemainingBalance());
            System.out.println("Total charged to demo balance: PHP " + result.getTotal());
            System.out.println("Remaining demo balance: PHP " + customer.getBalance());
            System.out.println("Checkout complete. Thank you for shopping!");
            return true;
        }

        Map<Product, Integer> customerCart = shoppingCart.get(customer);
        if (customerCart == null || customerCart.isEmpty()) {
            System.out.println("Your shopping cart is empty. Nothing to check out.");
            return false;
        }

        double total = getCartTotal(customer);
        if (total > customer.getBalance()) {
            System.out.println("Insufficient balance. Checkout failed.");
            return false;
        }

        System.out.println("\nDemo receipt for " + customer.getUsername() + ":");
        System.out.println("Selected payment method: " + paymentMethod.getDisplayName());
        System.out.println("Demo checkout only. No real payment was processed.");
        for (Map.Entry<Product, Integer> item : customerCart.entrySet()) {
            double itemTotal = item.getKey().getPrice() * item.getValue();
            System.out.println(item.getKey().getName() + " x " + item.getValue() + " = PHP " + itemTotal);
        }
        customer.setBalance(customer.getBalance() - total);
        System.out.println("Total charged to demo balance: PHP " + total);
        System.out.println("Remaining demo balance: PHP " + customer.getBalance());
        shoppingCart.remove(customer);
        System.out.println("Checkout complete. Thank you for shopping!");
        return true;
    }

    private double getCartTotal(Customer customer) throws SQLException {
        if (database != null) {
            double total = 0;
            for (Database.CartLine item : database.getCartItems(customer.getUsername())) {
                total += item.getLineTotal();
            }
            return total;
        }
        Map<Product, Integer> customerCart = shoppingCart.get(customer);
        if (customerCart == null) {
            return 0;
        }
        double total = 0;
        for (Map.Entry<Product, Integer> item : customerCart.entrySet()) {
            total += item.getKey().getPrice() * item.getValue();
        }
        return total;
    }
}
