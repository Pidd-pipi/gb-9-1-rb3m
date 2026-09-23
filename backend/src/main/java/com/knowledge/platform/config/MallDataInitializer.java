package com.knowledge.platform.config;

import com.knowledge.platform.entity.MallItem;
import com.knowledge.platform.repository.MallItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class MallDataInitializer implements ApplicationRunner {
    @Autowired
    private MallItemRepository mallItemRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (mallItemRepository.count() > 0) {
            return;
        }
        mallItemRepository.saveAll(List.of(
                item("5元课程优惠券", "无门槛使用，全站课程通用，有效期30天",
                        50, 50, new BigDecimal("5"), BigDecimal.ZERO),
                item("10元课程优惠券", "满100元可用，全站课程通用，有效期30天",
                        100, 20, new BigDecimal("10"), new BigDecimal("100")),
                item("限量课程优惠券（满200减30）", "限量发售，满200元可用，全站课程通用，有效期30天",
                        200, 5, new BigDecimal("30"), new BigDecimal("200"))
        ));
    }

    private MallItem item(String name, String description, int pointsCost, int stock,
                          BigDecimal discountValue, BigDecimal minAmount) {
        MallItem item = new MallItem();
        item.setName(name);
        item.setDescription(description);
        item.setPointsCost(pointsCost);
        item.setType(MallItem.ItemType.COUPON);
        item.setStock(stock);
        item.setDiscountValue(discountValue);
        item.setMinAmount(minAmount);
        item.setValidDays(30);
        item.setCreatedAt(LocalDateTime.now());
        return item;
    }
}
