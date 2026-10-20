# Daily Mart UI prototype

This browser UI connects to the PostgreSQL-backed Java API when served by the
Java application. The Node server below is only a static-file preview; it does
not provide API endpoints.

## Run locally

From the repository root, run:

```powershell
node ui-ux/server.js
```

For the connected application, start the Java web server from the repository
root with `run-web.bat`, then open <http://127.0.0.1:8765>. `PORT` can select a
different port. To view only the static design, run `node ui-ux/server.js`.

Guests can browse and save a cart in browser storage. Signing in or registering
merges saved items into the customer account cart; checkout requires sign-in.
Payment is selected on the cart page and confirmed in a separate dialog.

Administrators can add products from the admin dashboard. Product names must
be unique, prices are stored in PHP with up to two decimal places, and stock is
tracked in whole units. New products appear in the customer catalog
immediately; checkout deducts purchased quantities from stock. Existing
products receive an initial stock of 100 units when the database is upgraded.

Choose Customer or Admin from the sign-in screen; admin sign-in opens its own
access page. Admins see a Dashboard link instead of the customer cart. Only a
signed-in administrator who confirms their password can register another
administrator account.
