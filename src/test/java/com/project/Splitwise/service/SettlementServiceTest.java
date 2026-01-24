package com.project.Splitwise.service;

import com.project.Splitwise.dto.SettlementResponse;
import com.project.Splitwise.model.Balance;
import com.project.Splitwise.repository.BalanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


import java.math.BigDecimal;
import java.util.List;

public class SettlementServiceTest {
    @Mock
    private BalanceRepository balanceRepository;

    @InjectMocks
    private SettlementService settlementService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void shouldCalculateSettlementsCorrectly() {
        // GIVEN
        Long groupId = 10L;

        List<Balance> balances = List.of(
                new Balance(1L, groupId, BigDecimal.valueOf(500)),
                new Balance(2L, groupId, BigDecimal.valueOf(-300)),
                new Balance(3L, groupId, BigDecimal.valueOf(-200))
        );

        when(balanceRepository.findByGroupId(groupId))
                .thenReturn(balances);

        // WHEN
        SettlementResponse response = settlementService.getSettlements(groupId);

        // THEN
        assertNotNull(response);
        assertEquals(2, response.getSettlements().size());

        assertEquals(
                BigDecimal.valueOf(300),
                response.getSettlements().get(0).getAmount()
        );

    }

    @Test
    void shouldReturnEmptySettlementsWhenNoBalancesExist() {
        // GIVEN
        Long groupId = 20L;
        when(balanceRepository.findByGroupId(groupId))
                .thenReturn(List.of());

        // WHEN
        SettlementResponse response = settlementService.getSettlements(groupId);

        // THEN
        assertNotNull(response);
        assertTrue(response.getSettlements().isEmpty());
    }


    @Test
    void shouldReturnNoSettlementsWhenOnlyOneUserHasBalance() {
        // GIVEN
        Long groupId = 30L;

        List<Balance> balances = List.of(
                new Balance(1L, groupId, BigDecimal.valueOf(100))
        );

        when(balanceRepository.findByGroupId(groupId))
                .thenReturn(balances);

        // WHEN
        SettlementResponse response = settlementService.getSettlements(groupId);

        // THEN
        assertNotNull(response);
        assertTrue(response.getSettlements().isEmpty());
    }


}
