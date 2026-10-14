package com.conveniencestore;

// Represents a product sold in the store.
class Product {
    private final long productId;
    private String name;
    private double price;

    public Product(String name, double price) {
        this(0, name, price);
    }

    Product(long productId, String name, double price) {
        this.productId = productId;
        this.name = name;
        this.price = price;
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

    @Override
    public String toString() {
        return name + " (Price: PHP " + price + ")";
    }
}
