package com.knowledge.platform.service;

import com.knowledge.platform.dto.ApiResponse;
import com.knowledge.platform.entity.Coupon;
import com.knowledge.platform.entity.MallItem;
import com.knowledge.platform.entity.PointsAccount;
import com.knowledge.platform.repository.CouponRepository;
import com.knowledge.platform.repository.MallItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class MallService {

    private static final Logger log = LoggerFactory.getLogger(MallService.class);

    /** 兑换中标记的 Redis key 前缀，用于拦截同一用户连续/并发点击 */
    private static final String REDEEM_LOCK_PREFIX = "points-mall:redeem-lock:";

    /** 单实例内按“用户+商品”维度的锁，Redis 不可用时也能阻止连续点击 */
    private final ConcurrentHashMap<String, ReentrantLock> localLocks = new ConcurrentHashMap<>();

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
        return mallItemRepository.findAllByOrderByCreatedAtAsc();
    }

    public Page<Coupon> listMyCoupons(String userId, int page, int size) {
        return couponRepository.findByUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    /**
     * 兑换商城商品：原子地扣减积分、减少库存，并生成带唯一兑换码的优惠券。
     * 通过库存原子扣减（条件更新）保证多人争最后一份时只有一笔成功；
     * 通过分布式锁 + 前端禁用按钮保证同一用户连续点击不会重复兑换。
     * 任何一步失败都会补偿之前的操作，绝不可能出现扣了积分却没拿到券。
     */
    public ApiResponse<Coupon> redeem(String userId, String itemId) {
        MallItem item = mallItemRepository.findById(itemId).orElse(null);
        if (item == null) {
            return ApiResponse.error("商品不存在或已下架");
        }

        // 1. 快速失败：库存不足
        if (item.getStock() == null || item.getStock() <= 0) {
            return ApiResponse.error("「" + item.getName() + "」已兑换完毕，请关注后续补货");
        }

        // 2. 快速失败：积分不足
        PointsAccount account = pointsService.getAccount(userId);
        if (account.getBalance() == null || account.getBalance() < item.getPointsCost()) {
            return ApiResponse.error("积分不足，兑换需要 " + item.getPointsCost()
                    + " 积分，当前余额为 " + account.getBalance() + " 积分");
        }

        // 3. 兑换中锁：拦截同一用户的连续/并发点击
        //    先用单实例内的本地锁，再用 Redis 锁（多实例部署时生效），TTL 兜底防死锁
        String lockKey = REDEEM_LOCK_PREFIX + userId + ":" + itemId;
        ReentrantLock localLock = localLocks.computeIfAbsent(lockKey, k -> new ReentrantLock());
        boolean localAcquired;
        try {
            localAcquired = localLock.tryLock(0, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ApiResponse.error("兑换被中断，请重试");
        }
        if (!localAcquired) {
            return ApiResponse.error("正在处理上一笔兑换，请勿重复点击");
        }
        Boolean redisLocked = null;
        try {
            redisLocked = acquireLock(lockKey);
            if (Boolean.FALSE.equals(redisLocked)) {
                return ApiResponse.error("正在处理上一笔兑换，请勿重复点击");
            }
            return doRedeem(userId, item);
        } finally {
            if (!Boolean.FALSE.equals(redisLocked)) {
                releaseLock(lockKey);
            }
            localLock.unlock();
        }
    }

    private ApiResponse<Coupon> doRedeem(String userId, MallItem item) {
        int cost = item.getPointsCost();

        // 锁内再读一次，防止拿到锁时状态已变化
        item = mallItemRepository.findById(item.getId()).orElse(null);
        if (item == null) {
            return ApiResponse.error("商品不存在或已下架");
        }
        if (item.getStock() == null || item.getStock() <= 0) {
            return ApiResponse.error("「" + item.getName() + "」已兑换完毕，请关注后续补货");
        }
        PointsAccount account = pointsService.getAccount(userId);
        if (account.getBalance() == null || account.getBalance() < cost) {
            return ApiResponse.error("积分不足，兑换需要 " + cost
                    + " 积分，当前余额为 " + account.getBalance() + " 积分");
        }

        // 4. 原子扣减库存：只有 stock > 0 才扣减，争抢最后一份时仅一笔成功
        Query stockQuery = new Query(
                Criteria.where("_id").is(item.getId()).and("stock").gt(0));
        Update stockUpdate = new Update()
                .inc("stock", -1)
                .set("updatedAt", LocalDateTime.now());
        boolean stockDebited = mongoTemplate.updateFirst(stockQuery, stockUpdate, MallItem.class)
                .getModifiedCount() > 0;
        if (!stockDebited) {
            return ApiResponse.error("手慢了，「" + item.getName() + "」刚被兑换完了");
        }

        // 5. 原子扣减积分（条件更新，余额不足不会扣成负数）
        boolean pointsDebited = pointsService.debitPoints(userId, cost);
        if (!pointsDebited) {
            // 积分扣减失败：把库存还回去，整个兑换当作未发生
            restoreStock(item.getId());
            account = pointsService.getAccount(userId);
            return ApiResponse.error("积分不足，兑换需要 " + cost
                    + " 积分，当前余额为 " + account.getBalance() + " 积分");
        }

        // 6. 生成带唯一兑换码的优惠券。保存失败则补偿积分与库存
        Coupon coupon;
        try {
            coupon = buildCoupon(userId, item);
            coupon = couponRepository.save(coupon);
        } catch (Exception e) {
            log.error("兑换优惠券失败，开始补偿: userId={}, itemId={}", userId, item.getId(), e);
            pointsService.refundPoints(userId, cost);
            restoreStock(item.getId());
            return ApiResponse.error("兑换失败，积分已原路退回，请稍后重试");
        }

        // 7. 写入积分扣减流水（积分明细中可见）。券已发放，流水写入失败不回滚兑换
        try {
            pointsService.recordSpend(userId, cost, "兑换「" + item.getName() + "」优惠券");
        } catch (Exception e) {
            log.error("积分扣减流水写入失败（券已发放）: userId={}, couponId={}", userId, coupon.getId(), e);
        }

        return ApiResponse.success("兑换成功，优惠券已发放", coupon);
    }

    /**
     * 库存补偿：兑换后续步骤失败时把占用的库存还回去。
     */
    private void restoreStock(String itemId) {
        Query query = new Query(Criteria.where("_id").is(itemId));
        Update update = new Update().inc("stock", 1).set("updatedAt", LocalDateTime.now());
        mongoTemplate.updateFirst(query, update, MallItem.class);
    }

    private Coupon buildCoupon(String userId, MallItem item) {
        LocalDateTime now = LocalDateTime.now();
        int validDays = item.getValidDays() != null && item.getValidDays() > 0
                ? item.getValidDays() : 30;

        Coupon coupon = new Coupon();
        coupon.setUserId(userId);
        coupon.setCode(generateUniqueCode());
        coupon.setType(Coupon.CouponType.DISCOUNT);
        coupon.setItemId(item.getId());
        coupon.setItemName(item.getName());
        coupon.setDiscountValue(item.getDiscountValue() != null
                ? item.getDiscountValue() : BigDecimal.TEN);
        coupon.setMinAmount(item.getMinAmount() != null
                ? item.getMinAmount() : BigDecimal.ZERO);
        coupon.setValidFrom(now);
        coupon.setValidUntil(now.plusDays(validDays).truncatedTo(ChronoUnit.SECONDS));
        coupon.setUsed(false);
        coupon.setCreatedAt(now);
        return coupon;
    }

    /**
     * 生成全局唯一兑换码：KP + 12 位随机十六进制大写，依赖 code 唯一索引兜底。
     */
    private String generateUniqueCode() {
        for (int i = 0; i < 5; i++) {
            String code = "KP" + UUID.randomUUID().toString().replace("-", "")
                    .substring(0, 12).toUpperCase();
            if (!couponRepository.existsByCode(code)) {
                return code;
            }
        }
        // 极小概率碰撞时退化为完整 UUID，唯一性由索引最终保证
        return "KP" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }

    private Boolean acquireLock(String key) {
        try {
            return stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        } catch (Exception e) {
            // Redis 不可用时不阻断主流程，库存的原子扣减仍是最终正确性保证
            log.warn("获取兑换锁失败，跳过 Redis 锁: {}", e.getMessage());
            return true;
        }
    }

    private void releaseLock(String key) {
        try {
            stringRedisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("释放兑换锁失败: {}", e.getMessage());
        }
    }
}
