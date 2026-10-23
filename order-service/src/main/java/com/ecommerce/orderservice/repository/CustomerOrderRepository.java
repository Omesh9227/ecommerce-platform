package com.ecommerce.orderservice.repository;

import com.ecommerce.orderservice.entity.CustomerOrder;
import com.ecommerce.orderservice.entity.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerOrderRepository extends JpaRepository<CustomerOrder, UUID>, JpaSpecificationExecutor<CustomerOrder> {

    Optional<CustomerOrder> findByOrderNumber(String orderNumber);

    /** The order with its items loaded, for use after the transaction has ended. */
    @EntityGraph(attributePaths = "items")
    Optional<CustomerOrder> findWithItemsById(UUID id);

    /** Locks the order (SELECT ... FOR UPDATE) so two status changes for the same order run one after the other. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM CustomerOrder o WHERE o.orderNumber = :orderNumber")
    Optional<CustomerOrder> findByOrderNumberForUpdate(@Param("orderNumber") String orderNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM CustomerOrder o WHERE o.id = :id")
    Optional<CustomerOrder> findByIdForUpdate(@Param("id") UUID id);

    Page<CustomerOrder> findByUserIdAndStatusIn(UUID userId, Collection<OrderStatus> statuses, Pageable pageable);

    /** Ids of orders in {@code status} whose payment deadline has passed (used with PENDING_PAYMENT). */
    @Query("SELECT o.id FROM CustomerOrder o WHERE o.status = :status AND o.paymentDueAt < :now ORDER BY o.paymentDueAt")
    List<UUID> findDueBefore(@Param("status") OrderStatus status, @Param("now") Instant now, Pageable pageable);

    /** Ids of orders in {@code status} created before {@code before} (used to find checkouts that never finished). */
    @Query("SELECT o.id FROM CustomerOrder o WHERE o.status = :status AND o.createdAt < :before ORDER BY o.createdAt")
    List<UUID> findCreatedBefore(@Param("status") OrderStatus status, @Param("before") Instant before, Pageable pageable);
}
