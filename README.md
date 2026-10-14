# Convenience Store Console App

A Java console application that simulates a simple shopping experience. Customers
can create an account, log in, browse a sample product list, make purchases with
their account balance, and view their shopping cart. Items are charged to the
customer's demo balance only when they choose **Checkout**. Checkout lets the
customer select Cash on Delivery, GCash, or Card as a demo payment method. These
choices do not contact a payment provider or process real payments; the demo
balance is still deducted for each successful checkout.
After adding an item, customers can add another product or return to the main
menu.
An admin can log in to view registered customer usernames and balances, but
cannot view customer passwords.

The sample product names are shown in English. Prices are displayed in
Philippine pesos (PHP) and are illustrative estimates for common Philippine
grocery pack sizes. Actual prices vary by brand, retailer, and location.

---

![Application preview](https://github.com/T0MM11Y/MinimarketConsoleApp/blob/main/preview.png?raw=true)

## Use case diagram

```mermaid
flowchart LR
    customer["Customer"]
    admin["Admin"]

    subgraph system["Convenience Store Console App"]
        direction TB
        register(("Register account"))
        login(("Log in"))
        setBalance(("Set starting balance"))
        browse(("Browse products"))
        addToCart(("Add product to cart"))
        viewCart(("View shopping cart"))
        checkout(("Checkout"))
        validateBalance(("Validate available balance"))
        choosePayment(("Choose COD, GCash, or Card"))
        completePurchase(("Deduct payment, print receipt, and clear cart"))
        viewCustomers(("View customer usernames and balances"))
        adminLogin(("Admin login"))
        logout(("Log out"))
    end

    customer --- register
    customer --- login
    customer --- setBalance
    customer --- browse
    customer --- addToCart
    customer --- viewCart
    customer --- checkout
    customer --- choosePayment
    customer --- logout
    admin --- adminLogin
    admin --- viewCustomers

    checkout -. "includes" .-> validateBalance
    checkout -. "includes" .-> choosePayment
    checkout -. "includes" .-> completePurchase
    adminLogin -. "allows" .-> viewCustomers
```

For this in-memory demo, the admin login is `admin` / `admin123`. These are
demo-only credentials and must not be used in a real deployment. User accounts
and balances are not persisted when the application exits.

## Entity Relationship Diagram (ERD)

The application behaves like a lightweight retail system with a customer, a
product catalog, a shopping cart, and a checkout process. Although the project
stores data in memory instead of in a database, the data model can still be
represented as an ERD.

```mermaid
erDiagram
    CUSTOMER ||--o{ CART_ITEM : owns
    CUSTOMER ||--o{ TRANSACTION : performs
    PRODUCT ||--o{ CART_ITEM : included_in
    TRANSACTION ||--o{ CART_ITEM : contains
    TRANSACTION }o--|| PAYMENT_METHOD : uses
    ADMIN ||--o| CUSTOMER : manages

    CUSTOMER {
        string username PK
        string password
        decimal balance
    }

    PRODUCT {
        int product_id PK
        string name
        decimal price
    }

    CART_ITEM {
        int cart_item_id PK
        string username FK
        int product_id FK
        int quantity
        decimal line_total
    }

    TRANSACTION {
        int transaction_id PK
        string username FK
        string payment_method
        decimal total_amount
        datetime checkout_time
    }

    PAYMENT_METHOD {
        string payment_method PK
        string display_name
    }

    ADMIN {
        string username PK
        string password
    }
```

### Detailed explanation

#### 1. Customer entity
The `Customer` entity represents a registered shopper in the convenience store.
In the code, each customer is created with:
- `username`
- `password`
- `balance`

The `Login` class stores all customer accounts in a map keyed by username, so
there is effectively a one-to-many relationship between the system and its
customer records. A customer can log in, set an initial balance, add products
to the cart, and complete checkout.

Business rules:
- A username must be unique in the system.
- A customer may not check out if the cart total exceeds the available balance.
- A customer balance is reduced only after a successful checkout.

#### 2. Product entity
The `Product` entity describes each item sold by the store. Each product has:
- `name`
- `price`

In the app, products are created in `Main.java` and stored in a list inside the
`Store` object. This represents the catalog. A product can be selected by the
customer, added to the cart, and purchased during checkout.

Business rules:
- Product price is fixed at the time the item is added to the catalog.
- A product can appear multiple times in many carts, as long as each cart entry
  keeps its own quantity.
- The cart only validates the selected product number and quantity before
  accepting the item.

#### 3. Cart item entity
The `Store` class uses a `Map<Customer, Map<Product, Integer>>` structure. This
means each customer has a shopping cart, and each cart contains product-quantity
pairs. This is conceptually a many-to-many relationship between `Customer` and
`Product`, resolved through a bridge entity called `CartItem`.

A cart item usually contains:
- customer reference
- product reference
- quantity
- line total

This is the most important relationship in the ERD because it captures the
actual purchase intent. It also preserves repeated items in the cart—for example,
three packs of noodles or two bottles of cooking oil.

Business rules:
- Quantity must be greater than zero.
- The cart total cannot exceed the customer balance when a product is added.
- A customer can have multiple products in their cart at the same time.

#### 4. Transaction entity
When the customer chooses checkout, the application computes the total cost of
all cart items and then deducts the total from the customer balance. This is the
logical equivalent of a transaction.

The transaction concept includes:
- customer who performed the purchase
- selected payment method
- total amount
- time of checkout

Even though the app does not persist a database table named `Transaction`, this
is the natural entity that captures the business event of a purchase.

Business rules:
- Checkout is only allowed when the cart is not empty.
- Transaction total must be less than or equal to the customer balance.
- The cart is cleared immediately after a successful checkout.

#### 5. Payment method entity
The app uses an enum named `PaymentMethod` with the values:
- `CASH_ON_DELIVERY`
- `GCASH`
- `CARD`

This is represented in the ERD as a lookup table or enumerated entity. It lets
an order or transaction indicate how the customer intends to complete payment,
without performing a real payment gateway operation.

Business rules:
- Only valid payment methods are accepted.
- The selected method is printed on the receipt as part of the demo checkout.

#### 6. Admin entity
The admin is not a normal customer account. Instead, the app has hard-coded
credentials:
- username: `admin`
- password: `admin123`

The admin is allowed to view registered customer usernames and balances, but the
application deliberately does not reveal customer passwords. This gives the ERD a
separate administrative role without mixing admin logic with customer data.

Business rules:
- Admin must log in separately from customer users.
- Admin cannot register or buy products through the customer workflow.
- Admin sees account summaries only, not personal password information.

### Relationship summary

The core relationships are:
- One `Customer` has many `CartItem` rows.
- One `Product` can belong to many `CartItem` rows.
- One `Customer` performs many `Transaction` records.
- One `Transaction` contains many `CartItem` entries.
- One `Transaction` uses exactly one payment method.

This structure matches the real purchase flow in the application:
1. Customer registers and logs in.
2. Customer selects products.
3. Selected items are stored as cart entries.
4. Customer checks out.
5. Total is deducted from balance and the cart is cleared.

### Why this ERD matters

Even though this project stores everything in memory, the ERD helps explain the
logic behind the code. It clarifies how the app models customer accounts,
product inventory, cart entries, and purchases. This is useful for future
improvements such as replacing the in-memory map with a real database, adding
order history, introducing stock management, or expanding the admin system.

## Run from the console

Install a Java Development Kit (JDK) and make sure both `java` and `javac` are
available on your `PATH`. On Windows, run `run.bat` from the project folder or
double-click it. The script compiles the application and starts the interactive
console program.

## Import and run in Eclipse

1. Install a JDK (Java 8 or newer) and Eclipse IDE for Java Developers.
2. In Eclipse, choose **File → Import → Maven → Existing Maven Projects**.
3. Set the root directory to the `minimarketkompleks` folder and finish the
   import. Eclipse may take a moment to download Maven dependencies.
4. Select the imported project and choose **Project → Clean** if Eclipse shows
   stale build errors.
5. Open `src/main/java/com/conveniencestore/Main.java`, then choose **Run As →
   Java Application**. Alternatively, use the included
   `Convenience Store Console App.launch` run configuration.

The Maven project targets Java 8 bytecode and uses the JUnit dependency for
tests. If Eclipse reports an execution environment error, configure the
workspace JRE under **Window → Preferences → Java → Installed JREs** and select
a JDK 8 or newer.
