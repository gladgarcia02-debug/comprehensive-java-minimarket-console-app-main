package com.conveniencestore;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Scanner;

public class Main {

    public static void clearScreen() {
        try {
            if (System.getProperty("os.name").contains("Windows")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                Runtime.getRuntime().exec("clear");
            }
        } catch (IOException | InterruptedException e) {
            System.out.println("Could not clear the screen.");
        }
    }

    private static void addProductsToCart(Store store, Customer customer, Scanner scanner) throws SQLException {
        boolean addAnotherProduct = true;
        while (addAnotherProduct) {
            clearScreen();
            store.displayProducts();
            System.out.println();
            System.out.print("Enter the product number: ");
            int productNumber = scanner.nextInt();
            System.out.print("Enter the quantity: ");
            int quantity = scanner.nextInt();
            scanner.nextLine(); // Consume the newline.
            store.addToCart(customer, productNumber, quantity);

            int choice;
            do {
                System.out.println("\n1. Add another product");
                System.out.println("2. Return to the main menu");
                System.out.print("Choose an option (1/2): ");
                choice = scanner.nextInt();
                scanner.nextLine(); // Consume the newline.
                if (choice != 1 && choice != 2) {
                    System.out.println("Invalid option. Please choose 1 or 2.");
                }
            } while (choice != 1 && choice != 2);

            addAnotherProduct = choice == 1;
        }
    }

    public static void main(String[] args) {
        try {
            Database database = Database.fromEnvironment();
            database.initialize();
            run(database);
        } catch (SQLException | IllegalStateException e) {
            System.err.println("Unable to start the PostgreSQL-backed application: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void run(Database database) throws SQLException {
        Store store = new Store(database);
        Login login = new Login(database);
        Scanner scanner = new Scanner(System.in);

        if (store.isProductCatalogEmpty()) {
            store.addProduct(new Product("Bath soap bar, 90 g", 35.0));
            store.addProduct(new Product("Shampoo sachet, 12 ml", 8.0));
            store.addProduct(new Product("Toothpaste, 50 g", 45.0));
            store.addProduct(new Product("Cooking oil, 1 L", 110.0));
            store.addProduct(new Product("Powdered milk sachet, 33 g", 12.0));
            store.addProduct(new Product("Laundry detergent sachet, 55 g", 9.0));
            store.addProduct(new Product("Instant noodles, 55 g pack", 15.0));
            store.addProduct(new Product("Tea bags, 25-count box", 85.0));
            store.addProduct(new Product("White sugar, 1 kg", 75.0));
            store.addProduct(new Product("3-in-1 coffee sachet, 20 g", 10.0));
            store.addProduct(new Product("Facial tissues, 50 sheets", 35.0));
            store.addProduct(new Product("Regular-milled rice, 1 kg", 50.0));
            store.addProduct(new Product("Canned sardines, 155 g", 25.0));
            store.addProduct(new Product("Iodized salt, 500 g", 20.0));
            store.addProduct(new Product("Soy sauce, 350 ml", 25.0));
            store.addProduct(new Product("Fresh milk, 1 L", 95.0));
            store.addProduct(new Product("Ground coffee, 100 g", 120.0));
            store.addProduct(new Product("Toothbrush, 1 piece", 30.0));
            store.addProduct(new Product("Hairbrush, 1 piece", 75.0));
            store.addProduct(new Product("Chicken egg, 1 piece", 9.0));
        }

        while (true) {
            if (login.isAdminLoggedIn()) {
                clearScreen();
                System.out.println("\nAdmin menu:");
                System.out.println("1. View registered customers");
                System.out.println("2. Logout");
                System.out.print("Choose an option (1/2): ");
                int adminChoice = scanner.nextInt();
                scanner.nextLine(); // Consume the newline.

                switch (adminChoice) {
                    case 1:
                        login.displayCustomerAccounts();
                        System.out.println("\nPress Enter to continue.");
                        scanner.nextLine();
                        break;
                    case 2:
                        login.logoutAdmin();
                        break;
                    default:
                        System.out.println("Invalid option. Please try again.");
                        break;
                }
            } else if (login.getCurrentCustomer() == null) {
                clearScreen();
                System.out.println("\nWelcome to the Convenience Store!");
                System.out.println("1. Login");
                System.out.println("2. Register");
                System.out.println("3. Exit");
                System.out.println("4. Admin login");
                System.out.print("Choose an option (1/2/3/4): ");
                int choice = scanner.nextInt();
                scanner.nextLine(); // Consume the newline.
                clearScreen();

                switch (choice) {
                    case 1:
                        System.out.print("Enter your username: ");
                        String username = scanner.nextLine();
                        System.out.print("Enter your password: ");
                        String password = scanner.nextLine();
                        login.login(username, password);
                        break;
                    case 2:
                        System.out.print("Choose a username: ");
                        username = scanner.nextLine();
                        System.out.print("Choose a password: ");
                        password = scanner.nextLine();
                        login.register(username, password);
                        break;
                    case 3:
                        System.exit(0);
                        break;
                    case 4:
                        System.out.print("Enter admin username: ");
                        String adminUsername = scanner.nextLine();
                        System.out.print("Enter admin password: ");
                        String adminPassword = scanner.nextLine();
                        login.loginAdmin(adminUsername, adminPassword);
                        break;
                    default:
                        System.out.println("Invalid option. Please try again.");
                        break;
                }
                clearScreen();
            } else {
                Customer currentCustomer = login.getCurrentCustomer();
                clearScreen();
                System.out.println("Welcome, " + currentCustomer.getUsername() + "!");
                System.out.println("Your balance: PHP " + currentCustomer.getBalance());

                // Ask for a starting balance if the customer has none.
                if (currentCustomer.getBalance() == 0) {
                    System.out.print("Enter your starting balance (PHP): ");
                    double startingBalance = scanner.nextDouble();
                    login.setCurrentCustomerBalance(startingBalance);
                    clearScreen();
                    System.out.println("Your balance is now: PHP " + currentCustomer.getBalance());
                }

                while (login.getCurrentCustomer() != null) { // Allow the customer to shop repeatedly.
                    System.out.println("\nMain menu:");
                    System.out.println("1. Browse products");
                    System.out.println("2. Add a product to your cart");
                    System.out.println("3. View shopping cart");
                    System.out.println("4. Checkout");
                    System.out.println("5. Logout");
                    System.out.print("Choose an option (1/2/3/4/5): ");
                    int choice = scanner.nextInt();
                    scanner.nextLine(); // Consume the newline.

                    switch (choice) {
                        case 1:
                            clearScreen();
                            store.displayProducts();
                            break;
                        case 2:
                            addProductsToCart(store, currentCustomer, scanner);
                            break;

                        case 3:
                            clearScreen();
                            System.out.println("Your balance: PHP " + currentCustomer.getBalance());
                            System.out.println('\n');
                            store.displayShoppingCart(currentCustomer);
                            break;
                        case 4:
                            clearScreen();
                            System.out.println("Choose a demo payment method:");
                            System.out.println("1. Cash on Delivery");
                            System.out.println("2. GCash");
                            System.out.println("3. Card");
                            System.out.print("Choose an option (1/2/3): ");
                            int paymentChoice = scanner.nextInt();
                            scanner.nextLine(); // Consume the newline.

                            PaymentMethod paymentMethod = null;
                            switch (paymentChoice) {
                                case 1:
                                    paymentMethod = PaymentMethod.CASH_ON_DELIVERY;
                                    break;
                                case 2:
                                    paymentMethod = PaymentMethod.GCASH;
                                    break;
                                case 3:
                                    paymentMethod = PaymentMethod.CARD;
                                    break;
                                default:
                                    System.out.println("Invalid payment method. Checkout cancelled.");
                                    break;
                            }
                            if (paymentMethod != null) {
                                store.checkout(currentCustomer, paymentMethod);
                            }
                            break;
                        case 5:
                            clearScreen();
                            login.logout();
                            break;
                        default:
                            System.out.println("Invalid option. Please try again.");
                            break;
                    }

                    // Log out automatically when the balance runs out.
                    if (login.getCurrentCustomer() != null && currentCustomer.getBalance() <= 0) {
                        System.out.println("Your balance is empty. Logging out.");
                        login.logout();
                    }
                }
            }
        }
    }
}
