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

    public String getUsername() {
        return username;
    }

    public boolean checkPassword(String password) {
        return this.password.equals(password);
    }

    public double getBalance() {
        return balance;
    }

    public void setBalance(double balance) {
        this.balance = balance;
    }
}
