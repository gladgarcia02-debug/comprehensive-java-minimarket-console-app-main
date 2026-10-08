package com.conveniencestore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.Test;

public class AppTest {
    @Test
    public void checkoutChargesBalanceAndClearsCart() {
        Store store = new Store();
        Customer customer = new Customer("test", "password");
        customer.setBalance(100);
        store.addProduct(new Product("Soap", 35));

        assertTrue(store.addToCart(customer, 1, 2));
        assertEquals(100, customer.getBalance(), 0.001);
        assertTrue(store.checkout(customer, PaymentMethod.GCASH));
        assertEquals(30, customer.getBalance(), 0.001);
        assertFalse(store.checkout(customer, PaymentMethod.GCASH));
    }

    @Test
    public void checkoutRejectsMissingPaymentMethodWithoutChangingCartOrBalance() {
        Store store = new Store();
        Customer customer = new Customer("test", "password");
        customer.setBalance(100);
        store.addProduct(new Product("Soap", 35));

        assertTrue(store.addToCart(customer, 1, 1));
        assertFalse(store.checkout(customer, null));
        assertEquals(100, customer.getBalance(), 0.001);
        assertTrue(store.checkout(customer, PaymentMethod.CASH_ON_DELIVERY));
        assertEquals(65, customer.getBalance(), 0.001);
    }

    @Test
    public void cartRejectsInvalidQuantityAndItemsOverBalance() {
        Store store = new Store();
        Customer customer = new Customer("test", "password");
        customer.setBalance(50);
        store.addProduct(new Product("Soap", 35));

        assertFalse(store.addToCart(customer, 1, 0));
        assertFalse(store.addToCart(customer, 1, 2));
        assertFalse(store.addToCart(customer, 2, 1));
        assertEquals(50, customer.getBalance(), 0.001);
    }

    @Test
    public void adminCanViewCustomerNamesAndBalancesButNotPasswords() {
        Login login = new Login();
        login.register("customer1", "secret-password");
        assertTrue(login.login("customer1", "secret-password"));
        login.getCurrentCustomer().setBalance(75);
        login.logout();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOutput = System.out;
        try {
            System.setOut(new PrintStream(output));
            assertFalse(login.loginAdmin("admin", "wrong-password"));
            assertTrue(login.loginAdmin("admin", "admin123"));
            login.displayCustomerAccounts();
        } finally {
            System.setOut(originalOutput);
        }

        String displayedCustomers = output.toString();
        assertTrue(login.isAdminLoggedIn());
        assertTrue(displayedCustomers.contains("customer1 - Balance: PHP 75.0"));
        assertFalse(displayedCustomers.contains("secret-password"));
    }

    @Test
    public void adminUsernameCannotBeRegisteredAsCustomer() {
        Login login = new Login();
        login.register("admin", "customer-password");

        assertFalse(login.login("admin", "customer-password"));
        assertTrue(login.loginAdmin("admin", "admin123"));
    }
}
