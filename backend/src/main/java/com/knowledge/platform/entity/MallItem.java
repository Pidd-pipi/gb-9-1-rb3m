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

    private ItemType type;

    /**
     * 兑换所需积分
     */
    private Integer pointsCost;

    /**
     * 剩余库存（剩余份数）
     */
    private Integer stock;

    /**
     * 关联的课程/电子书 ID（类型为 AUDIO / EBOOK 时使用）
     */
    private String targetId;

    /**
     * 优惠券面值，类型为 COUPON 时使用
     */
    private BigDecimal discountValue;

    /**
     * 优惠券最低消费金额
     */
    private BigDecimal minAmount;

    /**
     * 兑换后优惠券有效天数
     */
    private Integer validDays;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public enum ItemType {
        COUPON,
        AUDIO,
        EBOOK
    }
}
