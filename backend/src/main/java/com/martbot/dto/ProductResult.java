package com.martbot.dto;

public class ProductResult {

    private String productId;
    private String name;
    private String url;
    private double price;
    private String imageUrl;
    private boolean available;

    public ProductResult() {}

    public ProductResult(String productId, String name, String url, double price, String imageUrl, boolean available) {
        this.productId = productId;
        this.name = name;
        this.url = url;
        this.price = price;
        this.imageUrl = imageUrl;
        this.available = available;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }
}
