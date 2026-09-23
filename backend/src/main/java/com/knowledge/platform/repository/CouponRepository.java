package com.knowledge.platform.repository;

import com.knowledge.platform.entity.Coupon;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CouponRepository extends MongoRepository<Coupon, String> {
    Page<Coupon> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
    boolean existsByCode(String code);
}
