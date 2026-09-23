package com.knowledge.platform.service;

import com.knowledge.platform.dto.ApiResponse;
import com.knowledge.platform.entity.Coupon;
import com.knowledge.platform.entity.MallItem;
import com.knowledge.platform.entity.PointsAccount;
import com.knowledge.platform.repository.CouponRepository;
import com.knowledge.platform.repository.MallItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class MallService {
    private static final Duration REDEEM_LOCK_TTL = Duration.ofSeconds(10);
    private static final int DEFAULT_VALID_DAYS = 30;

    @Autowired
    private MallItemRepository mallItemRepository;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private PointsService pointsService;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    public List<MallItem> listItems() {
        return mallItemRepository.findAll(Sort.by(Sort.Direction.ASC, "pointsCost"));
    }

    public ApiResponse<Coupon> redeem(String userId, String itemId) {
        String lockKey = "mall:redeem:" + userId + ":" + itemId;
        if (!tryLock(lockKey)) {
            return ApiResponse.error("兑换请求处理中，请勿重复提交");
        }
        try {
            MallItem item = mallItemRepository.findById(itemId).orElse(null);
            if (item == null) {
                return ApiResponse.error("商品不存在或已下架");
            }
            if (item.getType() != MallItem.ItemType.COUPON) {
                return ApiResponse.error("该商品暂不支持兑换");
            }

            PointsAccount account = pointsService.getOrCreateAccount(userId);
            if (account.getBalance() < item.getPointsCost()) {
                return ApiResponse.error("积分不足，还差 " + (item.getPointsCost() - account.getBalance()) + " 积分");
            }

            MallItem decremented = decrementStock(itemId);
            if (decremented == null) {
                return ApiResponse.error("手慢了，该商品已兑完");
            }

            boolean spent = pointsService.trySpendPoints(userId, item.getPointsCost(), "兑换商品：" + item.getName());
            if (!spent) {
                restoreStock(itemId);
                return ApiResponse.error("积分不足，兑换失败");
            }

            try {
                Coupon coupon = createCoupon(userId, item);
                return ApiResponse.success("兑换成功", coupon);
            } catch (Exception e) {
                pointsService.earnPoints(userId, item.getPointsCost(), "兑换失败退回：" + item.getName());
                restoreStock(itemId);
                return ApiResponse.error("兑换失败，积分已退回，请稍后重试");
            }
        } finally {
            unlock(lockKey);
        }
    }

    private MallItem decrementStock(String itemId) {
        Query query = Query.query(Criteria.where("id").is(itemId).and("stock").gt(0));
        Update update = new Update().inc("stock", -1);
        return mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), MallItem.class);
    }

    private void restoreStock(String itemId) {
        Query query = Query.query(Criteria.where("id").is(itemId));
        mongoTemplate.updateFirst(query, new Update().inc("stock", 1), MallItem.class);
    }

    private Coupon createCoupon(String userId, MallItem item) {
        LocalDateTime now = LocalDateTime.now();
        Coupon coupon = new Coupon();
        coupon.setUserId(userId);
        coupon.setCode(generateCode());
        coupon.setType(Coupon.CouponType.DISCOUNT);
        coupon.setDiscountValue(item.getDiscountValue() != null ? item.getDiscountValue() : BigDecimal.TEN);
        coupon.setMinAmount(item.getMinAmount() != null ? item.getMinAmount() : BigDecimal.ZERO);
        coupon.setValidFrom(now);
        coupon.setValidUntil(now.plusDays(item.getValidDays() != null ? item.getValidDays() : DEFAULT_VALID_DAYS));
        coupon.setUsed(false);
        coupon.setCreatedAt(now);
        return couponRepository.save(coupon);
    }

    private String generateCode() {
        for (int i = 0; i < 5; i++) {
            String code = "CPN" + UUID.randomUUID().toString().replace("-", "")
                    .substring(0, 12).toUpperCase();
            if (!couponRepository.existsByCode(code)) {
                return code;
            }
        }
        return "CPN" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }

    private boolean tryLock(String key) {
        try {
            Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", REDEEM_LOCK_TTL);
            return acquired == null || acquired;
        } catch (Exception e) {
            return true;
        }
    }

    private void unlock(String key) {
        try {
            stringRedisTemplate.delete(key);
        } catch (Exception ignored) {
        }
    }
}
