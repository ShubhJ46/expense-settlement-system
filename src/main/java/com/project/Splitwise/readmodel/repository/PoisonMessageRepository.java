package com.project.Splitwise.readmodel.repository;

import com.project.Splitwise.model.PoisonMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PoisonMessageRepository
        extends JpaRepository<PoisonMessage, Long> {
}

