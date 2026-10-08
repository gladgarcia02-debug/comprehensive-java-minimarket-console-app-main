package com.conveniencestore;

import java.util.HashMap;
import java.util.Map;

// Manages customer registration, login, and logout.
public class Login {
    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "admin123";

    private Map<String, Customer> customers;
    private Customer currentCustomer;
    private boolean adminLoggedIn;

    public Login() {
        customers = new HashMap<>();
    }

    // Logs in a customer using their username and password.
    public boolean login(String username, String password) {
        if (customers.containsKey(username)) {
            Customer customer = customers.get(username);
            if (customer.checkPassword(password)) {
                currentCustomer = customer;
                System.out.println("Login successful.");
                return true;
            }
        }
        System.out.println("Login failed. Please check your username and password.");
        return false;
    }

    public boolean loginAdmin(String username, String password) {
        if (ADMIN_USERNAME.equals(username) && ADMIN_PASSWORD.equals(password)) {
            adminLoggedIn = true;
            System.out.println("Admin login successful.");
            return true;
        }
        System.out.println("Admin login failed. Please check your username and password.");
        return false;
    }

    public boolean isAdminLoggedIn() {
        return adminLoggedIn;
    }

    public void displayCustomerAccounts() {
        System.out.println("Registered customers:");
        if (customers.isEmpty()) {
            System.out.println("No registered customers.");
            return;
        }
        for (Customer customer : customers.values()) {
            System.out.println(customer.getUsername() + " - Balance: PHP " + customer.getBalance());
        }
    }

    // Registers a new customer account.
    public void register(String username, String password) {
        if (ADMIN_USERNAME.equals(username)) {
            System.out.println("That username is reserved. Registration failed.");
            return;
        }
        if (!customers.containsKey(username)) {
            Customer customer = new Customer(username, password);
            customers.put(username, customer);
            System.out.println("Registration successful. Please log in.");
        } else {
            System.out.println("That username is already taken. Registration failed.");
        }
    }

    // Logs out the current customer.
    public void logout() {
        currentCustomer = null;
        System.out.println("You have been logged out.");
    }

    public void logoutAdmin() {
        adminLoggedIn = false;
        System.out.println("Admin has been logged out.");
    }

    // Returns the currently logged-in customer.
    public Customer getCurrentCustomer() {
        return currentCustomer;
    }
}
