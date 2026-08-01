package com.project.Splitwise.repository;

import com.project.Splitwise.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByGroupIdOrderByCreatedAtDesc(Long groupId);
}
