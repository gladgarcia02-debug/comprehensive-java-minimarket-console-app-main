package com.conveniencestore;

public class Customer {
    private String username;
    private String password;
    private double balance;

    public Customer(String username, String password) {
        this.username = username;
        this.password = password;
        this.balance = 0;
    }

    Customer(String username, double balance) {
        this.username = username;
        this.balance = balance;
    }

    public String getUsername() {
        return username;
    }

    public boolean checkPassword(String password) {
        return this.password != null && this.password.equals(password);
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }
}
