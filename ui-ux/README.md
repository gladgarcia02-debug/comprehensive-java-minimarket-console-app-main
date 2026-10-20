# Daily Mart browser UI

Daily Mart is a browser storefront backed by the Java HTTP API and PostgreSQL.
Customers can browse products, keep a guest cart in their browser, sign in or
create an account, and check out. Administrators manage the catalog and view
customer account balances from the admin dashboard.

## Run the connected app

1. Start PostgreSQL and create a database for the app.
2. Configure the repository-root `.env` file with `DATABASE_URL`,
   `DATABASE_USERNAME`, and `DATABASE_PASSWORD`. Set `ADMIN_USERNAME` and
   `ADMIN_PASSWORD` together to create the initial administrator account.
   Keep `.env` private; it contains credentials.
3. From the repository root, start the Java web app:

   ```powershell
   .\run-web.bat
   ```

4. Open <http://127.0.0.1:8765>. Set `PORT` before launch to use another port.

The Java server serves the UI and API from the same origin and initializes the
database schema on startup. Existing products receive an initial stock of 100
units when the inventory column is first added. The sample catalog is loaded
only when the products table is empty.

## Customer workflow

- Browse and search the catalog without signing in.
- Add products to a guest cart; it is saved in browser storage.
- Choose **Sign in** to select Customer or Admin. Customer registration signs
  the new customer in automatically. Guest cart items are merged into the
  customer cart after sign-in when available.
- Checkout requires a customer account. Choose Cash on Delivery, GCash, or Card
  in the cart, then review and confirm the order.
- Payment methods are for the demo only: no payment provider is contacted, and
  a successful order is charged to the customer's demo balance. Purchased
  quantities are deducted from stock.

## Administrator workflow

- Choose **Admin** on the sign-in screen and use the initial credentials from
  `.env`.
- The admin dashboard lets administrators add products with a name, PHP price,
  and stock quantity, and view customer usernames and demo balances. Product
  names must be unique; stock is a whole number from 0 to 1,000,000.
- Customers cannot see the dashboard. Signed-in admins see a **Dashboard**
  link instead of the customer cart.
- To create another administrator, choose **Register another admin** on the
  dashboard. An existing administrator must confirm their password. Admin
  passwords are stored as hashes.

## Static UI preview

To preview only the browser files without connecting to PostgreSQL or the Java
API, run this from the repository root:

```powershell
node .\ui-ux\server.js
```

This serves static files only; sign-in, product management, carts, and checkout
require the connected Java app.
