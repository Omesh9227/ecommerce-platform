package com.ecommerce.inventoryservice.repository;

import com.ecommerce.inventoryservice.entity.Reservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    Optional<Reservation> findByOrderReference(String orderReference);

    /** Locks the reservation so a release and a confirm for the same order can't run at the same time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Reservation r WHERE r.orderReference = :orderReference")
    Optional<Reservation> findByOrderReferenceForUpdate(@Param("orderReference") String orderReference);
}
