package com.knowledge.platform.repository;

import com.knowledge.platform.entity.Coupon;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CouponRepository extends MongoRepository<Coupon, String> {
    List<Coupon> findByUserIdOrderByCreatedAtDesc(String userId);

    boolean existsByCode(String code);
}
