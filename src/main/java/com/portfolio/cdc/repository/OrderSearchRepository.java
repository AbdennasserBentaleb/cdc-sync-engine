package com.portfolio.cdc.repository;

import com.portfolio.cdc.model.OrderDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderSearchRepository extends ElasticsearchRepository<OrderDocument, Integer> {
}
