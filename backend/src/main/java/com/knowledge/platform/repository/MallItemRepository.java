package com.knowledge.platform.repository;

import com.knowledge.platform.entity.MallItem;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MallItemRepository extends MongoRepository<MallItem, String> {
}
