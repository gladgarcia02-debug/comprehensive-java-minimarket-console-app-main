package com.conveniencestore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.junit.Test;

public class AppTest {
    @Test
    public void environmentConfigLoadsDotEnvValues() throws Exception {
        Path envFile = Files.createTempFile("minimarket-test", ".env");
        try {
            Files.write(envFile, (
                    "# sample settings\n"
                    + "DATABASE_URL=\"jdbc:postgresql://localhost:5432/minimarket\"\n"
                    + "export DATABASE_USERNAME=postgres\n"
                    + "DATABASE_PASSWORD=test-secret\n"
                    + "ADMIN_PASSWORD=\n").getBytes(StandardCharsets.UTF_8));

            Properties properties = EnvironmentConfig.parse(envFile);

            assertEquals("jdbc:postgresql://localhost:5432/minimarket",
                    properties.getProperty("DATABASE_URL"));
            assertEquals("postgres", properties.getProperty("DATABASE_USERNAME"));
            assertEquals("test-secret", properties.getProperty("DATABASE_PASSWORD"));
            assertEquals("", properties.getProperty("ADMIN_PASSWORD"));
        } finally {
            Files.deleteIfExists(envFile);
        }
    }

    @Test
    public void customerPasswordsAreStoredAsHashes() {
        String password = "test-password";
        String hash = PasswordHasher.hash(password);

        assertFalse(hash.contains(password));
        assertTrue(PasswordHasher.verify(password, hash));
        assertFalse(PasswordHasher.verify("wrong-password", hash));
    }

    @Test
    public void checkoutChargesBalanceAndClearsCart() throws Exception {
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
    public void checkoutRejectsMissingPaymentMethodWithoutChangingCartOrBalance() throws Exception {
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
    public void cartRejectsInvalidQuantityAndItemsOverBalance() throws Exception {
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
    public void adminCanViewCustomerNamesAndBalancesButNotPasswords() throws Exception {
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
    public void adminUsernameCannotBeRegisteredAsCustomer() throws Exception {
        Login login = new Login();
        login.register("admin", "customer-password");

        assertFalse(login.login("admin", "customer-password"));
        assertTrue(login.loginAdmin("admin", "admin123"));
    }
}
