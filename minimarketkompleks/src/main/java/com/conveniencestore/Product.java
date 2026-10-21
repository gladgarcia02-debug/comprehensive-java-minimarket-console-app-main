package com.conveniencestore;

// Represents a product sold in the store.
class Product {
    private final long productId;
    private String name;
    private double price;
    private int quantity;

    public Product(String name, double price) {
        this(0, name, price, 100);
    }

    Product(long productId, String name, double price) {
        this(productId, name, price, 100);
    }

    public Product(String name, double price, int quantity) {
        this(0, name, price, quantity);
    }

    Product(long productId, String name, double price, int quantity) {
        this.productId = productId;
        this.name = name;
        this.price = price;
        this.quantity = quantity;
    }

    public long getProductId() {
        return productId;
    }

    public String getName() {
        return name;
    }

    public double getPrice() {
        return price;
    }

    public int getQuantity() {
        return quantity;
    }

    void setQuantity(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("Product stock cannot be negative.");
        }
        this.quantity = quantity;
    }

    @Override
    public String toString() {
        return name + " (Price: PHP " + price + ")";
    }
}
