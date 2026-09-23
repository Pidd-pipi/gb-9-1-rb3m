package com.knowledge.platform.repository;

import com.knowledge.platform.entity.MallItem;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface MallItemRepository extends MongoRepository<MallItem, String> {
    List<MallItem> findAllByOrderByCreatedAtAsc();
    long count();
}
