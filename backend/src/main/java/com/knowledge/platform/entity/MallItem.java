package com.knowledge.platform.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Document(collection = "mall_items")
public class MallItem {
    @Id
    private String id;

    private String name;

    private String description;

    private Integer pointsCost;

    private ItemType type;

    private String targetId;

    private Integer stock;

    private String image;

    private BigDecimal discountValue;

    private BigDecimal minAmount;

    private Integer validDays;

    private LocalDateTime createdAt;

    public enum ItemType {
        COUPON,
        AUDIO,
        EBOOK
    }
}
