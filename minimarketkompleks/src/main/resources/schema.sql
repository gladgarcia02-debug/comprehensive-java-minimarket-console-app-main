CREATE TABLE IF NOT EXISTS customers (
    username VARCHAR(80) PRIMARY KEY,
    password_hash TEXT NOT NULL,
    balance NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (balance >= 0)
);

CREATE TABLE IF NOT EXISTS admins (
    username VARCHAR(80) PRIMARY KEY,
    password_hash TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS products (
    product_id BIGSERIAL PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    price NUMERIC(12, 2) NOT NULL CHECK (price >= 0)
);

CREATE TABLE IF NOT EXISTS payment_methods (
    payment_method VARCHAR(40) PRIMARY KEY,
    display_name VARCHAR(80) NOT NULL
);

CREATE TABLE IF NOT EXISTS transactions (
    transaction_id BIGSERIAL PRIMARY KEY,
    username VARCHAR(80) NOT NULL REFERENCES customers(username),
    payment_method VARCHAR(40) NOT NULL REFERENCES payment_methods(payment_method),
    total_amount NUMERIC(12, 2) NOT NULL CHECK (total_amount >= 0),
    checkout_time TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS cart_items (
    cart_item_id BIGSERIAL PRIMARY KEY,
    username VARCHAR(80) NOT NULL REFERENCES customers(username),
    product_id BIGINT NOT NULL REFERENCES products(product_id),
    transaction_id BIGINT REFERENCES transactions(transaction_id),
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    line_total NUMERIC(12, 2) NOT NULL CHECK (line_total >= 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS unique_open_cart_product
    ON cart_items (username, product_id)
    WHERE transaction_id IS NULL;

INSERT INTO payment_methods (payment_method, display_name)
VALUES
    ('CASH_ON_DELIVERY', 'Cash on Delivery'),
    ('GCASH', 'GCash'),
    ('CARD', 'Card')
ON CONFLICT (payment_method) DO NOTHING;
