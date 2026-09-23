package com.knowledge.platform.controller;

import com.knowledge.platform.dto.ApiResponse;
import com.knowledge.platform.entity.Coupon;
import com.knowledge.platform.repository.CouponRepository;
import com.knowledge.platform.security.CurrentUserUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/coupons")
public class CouponController {
    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private CurrentUserUtil currentUserUtil;

    @GetMapping
    public ApiResponse<List<Coupon>> myCoupons() {
        String userId = currentUserUtil.getCurrentUserId();
        if (userId == null) {
            return ApiResponse.error("请先登录");
        }
        return ApiResponse.success(couponRepository.findByUserIdOrderByCreatedAtDesc(userId));
    }
}
