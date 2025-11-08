package me.lubomirstankov.gotCraftWeb.model;

import com.google.gson.annotations.SerializedName;

import java.util.Map;

/**
 * Represents a payment notification payload from the web application.
 * This class is used to deserialize JSON messages received via Redis pub/sub.
 */
public class NotificationPayload {

    @SerializedName("event_type")
    private String eventType;

    @SerializedName("purchase_id")
    private Integer purchaseId;

    @SerializedName("user_id")
    private Integer userId;

    @SerializedName("username")
    private String username;

    @SerializedName("product_name")
    private String productName;

    @SerializedName("amount")
    private Double amount;

    @SerializedName("quantity")
    private Integer quantity;

    @SerializedName("status")
    private String status;

    @SerializedName("transaction_id")
    private String transactionId;

    @SerializedName("metadata")
    private Map<String, String> metadata;

    // Getters
    public String getEventType() {
        return eventType;
    }

    public Integer getPurchaseId() {
        return purchaseId;
    }

    public Integer getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getProductName() {
        return productName;
    }

    public Double getAmount() {
        return amount;
    }

    public Integer getQuantity() {
        return quantity;
    }


    public String getStatus() {
        return status;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    /**
     * Safely retrieves a metadata value by key.
     *
     * @param key The metadata key
     * @return The metadata value or empty string if not found
     */
    public String getMetadataValue(String key) {
        if (metadata != null && metadata.containsKey(key)) {
            return metadata.get(key);
        }
        return "";
    }

    @Override
    public String toString() {
        return "NotificationPayload{" +
                "eventType='" + eventType + '\'' +
                ", purchaseId=" + purchaseId +
                ", userId=" + userId +
                ", username='" + username + '\'' +
                ", productName='" + productName + '\'' +
                ", amount=" + amount +
                ", quantity=" + quantity +
                ", status='" + status + '\'' +
                ", transactionId='" + transactionId + '\'' +
                ", metadata=" + metadata +
                '}';
    }
}

