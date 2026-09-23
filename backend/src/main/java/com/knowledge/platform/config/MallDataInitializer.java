package com.knowledge.platform.config;

import com.knowledge.platform.entity.MallItem;
import com.knowledge.platform.repository.MallItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.IndexOperations;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.springframework.data.domain.Sort.Direction.ASC;

/**
 * 启动时确保积分商城可用：补齐索引并在商城为空时初始化限量课程优惠券。
 * 仅在商城没有任何商品时初始化，不会覆盖运营后续调整过的库存。
 */
@Component
public class MallDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(MallDataInitializer.class);

    @Autowired
    private MallItemRepository mallItemRepository;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    public void run(String... args) {
        ensureIndexes();
        seedMallItems();
    }

    /**
     * 补齐兑换流程依赖的索引，兼容已存在的旧数据库（init.js 仅在首次初始化时执行）。
     */
    private void ensureIndexes() {
        try {
            IndexOperations couponIndexOps = mongoTemplate.indexOps("coupons");
            ensureIndex(couponIndexOps, new Index().on("code", ASC).unique(), "code_1");
            ensureIndex(couponIndexOps, new Index().on("userId", ASC), "userId_1");

            IndexOperations mallIndexOps = mongoTemplate.indexOps("mall_items");
            ensureIndex(mallIndexOps, new Index().on("createdAt", ASC), "createdAt_1");
        } catch (Exception e) {
            log.warn("补齐积分商城索引时出错: {}", e.getMessage());
        }
    }

    private void ensureIndex(IndexOperations indexOps, Index index, String name) {
        boolean exists = indexOps.getIndexInfo().stream()
                .anyMatch(info -> name.equals(info.getName()));
        if (!exists) {
            indexOps.ensureIndex(index.named(name));
        }
    }

    private void seedMallItems() {
        if (mallItemRepository.count() > 0) {
            return;
        }

        log.info("积分商城商品为空，初始化限量课程优惠券");
        LocalDateTime now = LocalDateTime.now();

        mallItemRepository.save(buildItem(
                "限量精品课程10元优惠券",
                "限量发放，可用于抵扣平台内任意付费课程 10 元",
                100, 5, new BigDecimal("10"), BigDecimal.ZERO, 30, now));
        mallItemRepository.save(buildItem(
                "限量名师专栏30元优惠券",
                "限量发放，订阅付费专栏满 99 元可抵扣 30 元",
                300, 3, new BigDecimal("30"), new BigDecimal("99"), 60, now));
        mallItemRepository.save(buildItem(
                "限量新课体验5元优惠券",
                "限量发放，新课程专享，无门槛抵扣 5 元",
                50, 10, new BigDecimal("5"), BigDecimal.ZERO, 15, now));
    }

    private MallItem buildItem(String name, String description, int pointsCost, int stock,
                               BigDecimal discountValue, BigDecimal minAmount, int validDays,
                               LocalDateTime now) {
        MallItem item = new MallItem();
        item.setName(name);
        item.setDescription(description);
        item.setType(MallItem.ItemType.COUPON);
        item.setPointsCost(pointsCost);
        item.setStock(stock);
        item.setDiscountValue(discountValue);
        item.setMinAmount(minAmount);
        item.setValidDays(validDays);
        item.setCreatedAt(now);
        item.setUpdatedAt(now);
        return item;
    }
}
