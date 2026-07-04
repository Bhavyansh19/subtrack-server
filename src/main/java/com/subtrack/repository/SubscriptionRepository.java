package com.subtrack.repository;

import com.subtrack.model.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    List<Subscription> findByActiveTrue();

    @Query("SELECT s FROM Subscription s WHERE s.active = true " +
           "AND s.nextRenewalDate BETWEEN :today AND :cutoff " +
           "ORDER BY s.nextRenewalDate ASC")
    List<Subscription> findRenewingBetween(@Param("today") LocalDate today, @Param("cutoff") LocalDate cutoff);
}
