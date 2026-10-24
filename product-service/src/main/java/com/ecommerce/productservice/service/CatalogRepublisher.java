package com.ecommerce.productservice.service;

import com.ecommerce.productservice.dto.response.RepublishResponse;
import com.ecommerce.productservice.entity.Category;
import com.ecommerce.productservice.entity.Product;
import com.ecommerce.productservice.event.EventTypes;
import com.ecommerce.productservice.outbox.ProductEventPublisher;
import com.ecommerce.productservice.repository.CategoryRepository;
import com.ecommerce.productservice.repository.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/**
 * Sends the current state of the whole catalog again: CATEGORY_UPDATED for every category, then PRODUCT_UPDATED for
 * every product (any status), through the outbox like any other change. Used to fill a new consumer such as
 * search-service with data that existed before it did. Safe to run any time: the events carry the current versions,
 * and consumers that already have them change nothing.
 * Products are sent in batches of {@value #BATCH_SIZE}, one short transaction per batch.
 */
@Slf4j
@Service
public class CatalogRepublisher {

    static final int BATCH_SIZE = 100;

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductEventPublisher eventPublisher;
    private final TransactionTemplate transactionTemplate;

    public CatalogRepublisher(CategoryRepository categoryRepository, ProductRepository productRepository,
                              ProductEventPublisher eventPublisher, PlatformTransactionManager transactionManager) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.eventPublisher = eventPublisher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public RepublishResponse republishAll() {
        Integer categories = transactionTemplate.execute(status -> {
            List<Category> all = categoryRepository.findAll();
            all.forEach(c -> eventPublisher.category(EventTypes.CATEGORY_UPDATED, c, c.getVersion() == null ? 0 : c.getVersion()));
            return all.size();
        });
        int products = 0;
        int pageNumber = 0;
        boolean more = true;
        while (more) {
            final int current = pageNumber;
            Page<Product> page = transactionTemplate.execute(status -> {
                Page<Product> batch = productRepository.findAll(PageRequest.of(current, BATCH_SIZE, Sort.by("id")));
                batch.forEach(eventPublisher::productUpdated);
                return batch;
            });
            products += page.getNumberOfElements();
            more = page.hasNext();
            pageNumber++;
        }
        log.info("Republished {} categories and {} products", categories, products);
        return new RepublishResponse(categories == null ? 0 : categories, products);
    }
}
