package com.conveniencestore;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/** Local web adapter for the existing PostgreSQL-backed store operations. */
public final class WebServer {
    private static final String SESSION_COOKIE = "minimarket_session";
    private static final BigDecimal MAX_NUMERIC_AMOUNT = new BigDecimal("9999999999.99");
    private final Database database;
    private final Map<String, Session> sessions = new ConcurrentHashMap<String, Session>();

    private WebServer(Database database) {
        this.database = database;
    }

    public static void main(String[] args) {
        try {
            Database database = Database.fromEnvironment();
            database.initialize();
            WebServer app = new WebServer(database);
            app.seedProducts();
            int port = Integer.parseInt(System.getenv().containsKey("PORT")
                    ? System.getenv("PORT") : "8765");
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
            server.createContext("/api/", app::handleApi);
            server.createContext("/", app::serveUi);
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            System.out.println("Minimarket web app running at http://127.0.0.1:" + port);
        } catch (Exception e) {
            System.err.println("Unable to start minimarket web app: " + e.getMessage());
            System.exit(1);
        }
    }

    private void seedProducts() throws SQLException {
        if (!database.getProducts().isEmpty()) return;
        String[][] catalog = {
            {"Bath soap bar, 90 g", "35"}, {"Shampoo sachet, 12 ml", "8"},
            {"Toothpaste, 50 g", "45"}, {"Cooking oil, 1 L", "110"},
            {"Powdered milk sachet, 33 g", "12"}, {"Laundry detergent sachet, 55 g", "9"},
            {"Instant noodles, 55 g pack", "15"}, {"Tea bags, 25-count box", "85"},
            {"White sugar, 1 kg", "75"}, {"3-in-1 coffee sachet, 20 g", "10"},
            {"Facial tissues, 50 sheets", "35"}, {"Regular-milled rice, 1 kg", "50"},
            {"Canned sardines, 155 g", "25"}, {"Iodized salt, 500 g", "20"},
            {"Soy sauce, 350 ml", "25"}, {"Fresh milk, 1 L", "95"},
            {"Ground coffee, 100 g", "120"}, {"Toothbrush, 1 piece", "30"},
            {"Hairbrush, 1 piece", "75"}, {"Chicken egg, 1 piece", "9"}
        };
        for (String[] item : catalog) database.saveProduct(new Product(item[0], Double.parseDouble(item[1])));
    }

    private void handleApi(HttpExchange exchange) throws IOException {
        try {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            Map<String, String> form = "GET".equals(method) ? new HashMap<String, String>() : readForm(exchange);
            Session session = sessionFor(exchange);
            if ("GET".equals(method) && "/api/products".equals(path)) {
                List<Map<String, Object>> products = new ArrayList<Map<String, Object>>();
                for (Product product : database.getProducts()) {
                    Map<String, Object> item = new LinkedHashMap<String, Object>();
                    item.put("id", product.getProductId()); item.put("name", product.getName()); item.put("price", product.getPrice());
                    item.put("quantity", product.getQuantity());
                    products.add(item);
                }
                json(exchange, 200, products); return;
            }
            if ("POST".equals(method) && "/api/admin/products".equals(path)) {
                requireAdmin(session);
                String name = required(form, "name").trim();
                if (name.isEmpty() || name.length() > 200) throw new ApiError(400, "Product names must contain 1 to 200 characters.");
                int quantity = Integer.parseInt(required(form, "quantity"));
                if (quantity < 0 || quantity > 1000000) throw new ApiError(400, "Quantity must be between 0 and 1,000,000.");
                BigDecimal price;
                try {
                    price = new BigDecimal(required(form, "price"));
                } catch (NumberFormatException e) {
                    throw new ApiError(400, "Enter a valid product price.");
                }
                if (price.signum() < 0 || price.scale() > 2 || price.compareTo(MAX_NUMERIC_AMOUNT) > 0) {
                    throw new ApiError(400, "Price must be non-negative, have at most two decimal places, and not exceed 9,999,999,999.99.");
                }
                Product product;
                try {
                    product = database.createProduct(name, price, quantity);
                } catch (SQLException e) {
                    if ("23505".equals(e.getSQLState())) throw new ApiError(409, "A product with that name already exists.");
                    throw e;
                }
                Map<String, Object> item = new LinkedHashMap<String, Object>();
                item.put("id", product.getProductId()); item.put("name", product.getName()); item.put("price", product.getPrice());
                item.put("quantity", product.getQuantity());
                json(exchange, 201, item); return;
            }
            if ("POST".equals(method) && "/api/admin/register".equals(path)) {
                requireAdmin(session);
                String currentPassword = required(form, "currentPassword");
                if (!database.authenticateAdmin(session.username, currentPassword)) {
                    throw new ApiError(401, "Confirm your administrator password to register a new admin.");
                }
                String username = required(form, "username").trim();
                String password = required(form, "password");
                if (username.isEmpty() || username.length() > 80 || password.length() < 6) {
                    throw new ApiError(400, "Use a username up to 80 characters and a password of at least 6 characters.");
                }
                if (!database.createAdmin(username, password)) {
                    throw new ApiError(409, "An administrator with that username already exists.");
                }
                json(exchange, 201, message("Administrator account created."));
                return;
            }
            if ("POST".equals(method) && "/api/register".equals(path)) {
                String username = required(form, "username").trim(); String password = required(form, "password");
                if (username.isEmpty() || username.length() > 80 || password.length() < 6) throw new ApiError(400, "Use a username up to 80 characters and a password of at least 6 characters.");
                if (isReservedAdminUsername(username)) throw new ApiError(409, "That username is reserved for an administrator.");
                if (!database.registerCustomer(username, password)) throw new ApiError(409, "That username is already taken.");
                json(exchange, 201, message("Account created. Sign in to continue.")); return;
            }
            if ("POST".equals(method) && ("/api/login".equals(path) || "/api/admin/login".equals(path))) {
                String username = required(form, "username"); String password = required(form, "password");
                boolean admin = "/api/admin/login".equals(path);
                boolean valid = admin ? database.authenticateAdmin(username, password)
                        : database.authenticateCustomer(username, password) != null;
                if (!valid) throw new ApiError(401, "Username or password is incorrect.");
                String token = UUID.randomUUID().toString();
                sessions.put(token, new Session(username, admin));
                exchange.getResponseHeaders().add("Set-Cookie", SESSION_COOKIE + "=" + token + "; Path=/; HttpOnly; SameSite=Lax");
                json(exchange, 200, account(sessionFor(exchange, token))); return;
            }
            if ("POST".equals(method) && "/api/logout".equals(path)) {
                String token = cookie(exchange, SESSION_COOKIE); if (token != null) sessions.remove(token);
                exchange.getResponseHeaders().add("Set-Cookie", SESSION_COOKIE + "=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax");
                json(exchange, 200, message("Signed out.")); return;
            }
            if ("GET".equals(method) && "/api/me".equals(path)) {
                requireSession(session); json(exchange, 200, account(session)); return;
            }
            if ("POST".equals(method) && "/api/balance".equals(path)) {
                requireCustomer(session);
                BigDecimal balance;
                try { balance = new BigDecimal(required(form, "balance")); }
                catch (NumberFormatException e) { throw new ApiError(400, "Balance must be a valid amount."); }
                if (balance.signum() < 0 || balance.scale() > 2 || balance.compareTo(MAX_NUMERIC_AMOUNT) > 0) {
                    throw new ApiError(400, "Balance must be non-negative, have at most two decimal places, and not exceed 9,999,999,999.99.");
                }
                database.setCustomerBalance(session.username, balance.doubleValue());
                Map<String, Object> result = account(session); json(exchange, 200, result); return;
            }
            if ("GET".equals(method) && "/api/cart".equals(path)) {
                requireCustomer(session); json(exchange, 200, cart(session.username)); return;
            }
            if ("POST".equals(method) && "/api/cart/add".equals(path)) {
                requireCustomer(session); long productId = Long.parseLong(required(form, "productId")); int quantity = Integer.parseInt(required(form, "quantity"));
                if (quantity < 1 || quantity > 999) throw new ApiError(400, "Quantity must be between 1 and 999.");
                Product selected = null;
                for (Product p : database.getProducts()) if (p.getProductId() == productId) selected = p;
                if (selected == null) throw new ApiError(404, "Product not found.");
                if (!database.addCartItem(session.username, selected, quantity)) throw new ApiError(409, "This item could not be added to your cart.");
                json(exchange, 200, cart(session.username)); return;
            }
            if ("POST".equals(method) && "/api/cart/quantity".equals(path)) {
                requireCustomer(session); long productId = Long.parseLong(required(form, "productId")); int quantity = Integer.parseInt(required(form, "quantity"));
                if (quantity < 0 || quantity > 999) throw new ApiError(400, "Quantity must be between 0 and 999.");
                database.setCartQuantity(session.username, productId, quantity);
                json(exchange, 200, cart(session.username)); return;
            }
            if ("POST".equals(method) && "/api/checkout".equals(path)) {
                requireCustomer(session); PaymentMethod payment;
                try { payment = PaymentMethod.valueOf(required(form, "paymentMethod")); }
                catch (IllegalArgumentException e) { throw new ApiError(400, "Choose a valid payment method."); }
                Database.CheckoutResult result = database.checkout(session.username, payment);
                if (result.getStatus() == Database.CheckoutResult.Status.EMPTY_CART) throw new ApiError(409, "Your cart is empty.");
                if (result.getStatus() == Database.CheckoutResult.Status.INSUFFICIENT_BALANCE) throw new ApiError(409, "Your balance is too low for this order.");
                if (result.getStatus() == Database.CheckoutResult.Status.OUT_OF_STOCK) throw new ApiError(409, "One or more cart items are no longer available in the requested quantity. Update your cart and try again.");
                Map<String, Object> receipt = new LinkedHashMap<String, Object>();
                receipt.put("items", cartLineMaps(result.getItems())); receipt.put("total", result.getTotal());
                receipt.put("balance", result.getRemainingBalance()); receipt.put("paymentMethod", payment.getDisplayName());
                json(exchange, 200, receipt); return;
            }
            if ("GET".equals(method) && "/api/admin/customers".equals(path)) {
                requireAdmin(session); List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
                for (Customer customer : database.getCustomers()) {
                    Map<String, Object> row = new LinkedHashMap<String, Object>(); row.put("username", customer.getUsername()); row.put("balance", customer.getBalance()); rows.add(row);
                }
                json(exchange, 200, rows); return;
            }
            throw new ApiError(404, "Endpoint not found.");
        } catch (ApiError e) {
            json(exchange, e.status, message(e.getMessage()));
        } catch (NumberFormatException e) {
            json(exchange, 400, message("A numeric value is invalid."));
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("available stock")) {
                json(exchange, 409, message("That quantity exceeds the available product stock."));
            } else {
                e.printStackTrace(); json(exchange, 500, message("The database request failed."));
            }
        } catch (Exception e) {
            e.printStackTrace(); json(exchange, 500, message("The request could not be completed."));
        } finally { exchange.close(); }
    }

    private List<Map<String, Object>> cart(String username) throws SQLException {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Database.CartLine line : database.getCartItems(username)) {
            Map<String, Object> row = new LinkedHashMap<String, Object>(); row.put("productId", line.getProductId()); row.put("name", line.getName()); row.put("quantity", line.getQuantity());
            row.put("lineTotal", line.getLineTotal()); result.add(row);
        }
        return result;
    }
    private List<Map<String, Object>> cartLineMaps(List<Database.CartLine> lines) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Database.CartLine line : lines) { Map<String,Object> row = new LinkedHashMap<String,Object>(); row.put("productId",line.getProductId()); row.put("name",line.getName()); row.put("quantity",line.getQuantity()); row.put("lineTotal",line.getLineTotal()); result.add(row); }
        return result;
    }
    private Map<String, Object> account(Session session) throws SQLException {
        Map<String, Object> result = new LinkedHashMap<String, Object>(); result.put("username", session.username); result.put("role", session.admin ? "admin" : "customer");
        if (!session.admin) {
            for (Customer customer : database.getCustomers()) if (customer.getUsername().equals(session.username)) { result.put("balance", customer.getBalance()); break; }
        }
        return result;
    }
    private static Map<String, Object> message(String value) { Map<String,Object> result = new LinkedHashMap<String,Object>(); result.put("message",value); return result; }
    private static boolean isReservedAdminUsername(String username) {
        String configured = EnvironmentConfig.get(EnvironmentConfig.load(), "ADMIN_USERNAME");
        return username.equals(configured == null || configured.trim().isEmpty() ? "admin" : configured);
    }
    private Session sessionFor(HttpExchange exchange) { String token = cookie(exchange, SESSION_COOKIE); return token == null ? null : sessions.get(token); }
    private Session sessionFor(HttpExchange exchange, String token) { return sessions.get(token); }
    private static void requireSession(Session session) throws ApiError { if (session == null) throw new ApiError(401, "Please sign in to continue."); }
    private static void requireCustomer(Session session) throws ApiError { requireSession(session); if (session.admin) throw new ApiError(403, "This action is for customer accounts."); }
    private static void requireAdmin(Session session) throws ApiError { requireSession(session); if (!session.admin) throw new ApiError(403, "Administrator access is required."); }

    private void serveUi(HttpExchange exchange) throws IOException {
        String requestPath = exchange.getRequestURI().getPath();
        String name = "/".equals(requestPath) ? "index.html" : requestPath.substring(1);
        if (!"index.html".equals(name) && !"app.js".equals(name)) {
            respond(exchange, 404, "text/plain; charset=utf-8", "Not found".getBytes(StandardCharsets.UTF_8)); return;
        }
        Path file = Paths.get("ui-ux", name).toAbsolutePath().normalize();
        if (!Files.isRegularFile(file)) { respond(exchange, 404, "text/plain; charset=utf-8", "UI file not found. Run from the repository root.".getBytes(StandardCharsets.UTF_8)); return; }
        String contentType = "index.html".equals(name) ? "text/html; charset=utf-8" : "text/javascript; charset=utf-8";
        respond(exchange, 200, contentType, Files.readAllBytes(file));
    }
    private static Map<String, String> readForm(HttpExchange exchange) throws IOException {
        ByteArrayOutputStream body = new ByteArrayOutputStream(); byte[] buffer = new byte[2048]; int count;
        while ((count = exchange.getRequestBody().read(buffer)) != -1) { if (body.size() + count > 16384) throw new IOException("Request body too large."); body.write(buffer,0,count); }
        Map<String,String> values = new HashMap<String,String>(); String decoded = new String(body.toByteArray(), StandardCharsets.UTF_8);
        for (String pair : decoded.split("&")) { if (pair.isEmpty()) continue; String[] parts = pair.split("=",2); values.put(URLDecoder.decode(parts[0],"UTF-8"), parts.length == 1 ? "" : URLDecoder.decode(parts[1],"UTF-8")); }
        return values;
    }
    private static String required(Map<String,String> values, String key) throws ApiError { String value=values.get(key); if(value==null||value.trim().isEmpty()) throw new ApiError(400,"Required field missing: "+key); return value; }
    private static String cookie(HttpExchange exchange, String name) {
        List<String> cookies = exchange.getRequestHeaders().get("Cookie"); if (cookies == null) return null;
        for (String header : cookies) for (String piece : header.split(";")) { String[] pair=piece.trim().split("=",2); if(pair.length==2&&name.equals(pair[0])) return pair[1]; }
        return null;
    }
    private static void json(HttpExchange exchange, int status, Object value) throws IOException { respond(exchange,status,"application/json; charset=utf-8",toJson(value).getBytes(StandardCharsets.UTF_8)); }
    private static void respond(HttpExchange exchange,int status,String type,byte[] bytes) throws IOException {
        Headers headers=exchange.getResponseHeaders(); headers.set("Content-Type",type); headers.set("X-Content-Type-Options","nosniff"); headers.set("Cache-Control","no-store");
        headers.set("Connection", "close");
        exchange.sendResponseHeaders(status,bytes.length); exchange.getResponseBody().write(bytes);
    }
    private static String toJson(Object value) {
        if(value==null)return "null"; if(value instanceof String)return "\""+escape((String)value)+"\"";
        if(value instanceof Number||value instanceof Boolean)return String.valueOf(value);
        if(value instanceof Map){StringBuilder b=new StringBuilder("{");boolean first=true;for(Object entryObject:((Map<?,?>)value).entrySet()){Map.Entry<?,?>e=(Map.Entry<?,?>)entryObject;if(!first)b.append(',');first=false;b.append(toJson(String.valueOf(e.getKey()))).append(':').append(toJson(e.getValue()));}return b.append('}').toString();}
        if(value instanceof Iterable){StringBuilder b=new StringBuilder("[");boolean first=true;for(Object item:(Iterable<?>)value){if(!first)b.append(',');first=false;b.append(toJson(item));}return b.append(']').toString();}
        return toJson(String.valueOf(value));
    }
    private static String escape(String value) { StringBuilder b=new StringBuilder();for(int i=0;i<value.length();i++){char c=value.charAt(i);switch(c){case '"':b.append("\\\"");break;case '\\':b.append("\\\\");break;case '\n':b.append("\\n");break;case '\r':b.append("\\r");break;case '\t':b.append("\\t");break;default:if(c<32)b.append(String.format("\\u%04x",(int)c));else b.append(c);}}return b.toString(); }
    private static final class Session { final String username; final boolean admin; Session(String username,boolean admin){this.username=username;this.admin=admin;} }
    private static final class ApiError extends Exception { final int status; ApiError(int status,String message){super(message);this.status=status;} }
}
