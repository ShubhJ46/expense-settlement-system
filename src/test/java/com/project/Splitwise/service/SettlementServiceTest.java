package com.project.Splitwise.service;

import com.project.Splitwise.dto.SettlementResponse;
import com.project.Splitwise.model.Balance;
import com.project.Splitwise.repository.BalanceRepository;
import com.project.Splitwise.security.GroupAccess;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

    private static final Long GROUP_ID = 10L;

    @Mock
    private BalanceRepository balanceRepository;
    /** Permissive here; the authorization rules themselves are covered by AuthorizationIT. */
    @Mock
    private GroupAccess groupAccess;

    @InjectMocks
    private SettlementService settlementService;

    @Test
    void computesSettlementsForGroup() {
        // Balance's constructor is (groupId, userId, netBalance). The previous version of
        // this test passed the arguments transposed, which gave all three rows the same
        // userId and made the assertions meaningless.
        when(balanceRepository.findByGroupId(GROUP_ID)).thenReturn(List.of(
                new Balance(GROUP_ID, 1L, new BigDecimal("500.00")),
                new Balance(GROUP_ID, 2L, new BigDecimal("-300.00")),
                new Balance(GROUP_ID, 3L, new BigDecimal("-200.00"))));

        SettlementResponse response = settlementService.getSettlements(GROUP_ID);

        assertNotNull(response);
        assertEquals(GROUP_ID, response.getGroupId());
        assertEquals(2, response.getSettlements().size());

        // compareTo, not equals: BigDecimal.equals is scale-sensitive, so 300 != 300.00
        // and asserting with equals here would fail purely on formatting.
        assertEquals(0, response.getSettlements().get(0).getAmount().compareTo(new BigDecimal("300.00")));
        assertEquals(2L, response.getSettlements().get(0).getFromUserId());
        assertEquals(1L, response.getSettlements().get(0).getToUserId());
    }

    @Test
    void returnsEmptyWhenGroupHasNoBalances() {
        when(balanceRepository.findByGroupId(20L)).thenReturn(List.of());

        assertTrue(settlementService.getSettlements(20L).getSettlements().isEmpty());
    }

    @Test
    void returnsEmptyWhenOnlyOneUserHasABalance() {
        when(balanceRepository.findByGroupId(30L)).thenReturn(List.of(
                new Balance(30L, 1L, new BigDecimal("100.00"))));

        assertTrue(settlementService.getSettlements(30L).getSettlements().isEmpty());
    }

    @Test
    @DisplayName("computing settlements leaves the stored balances untouched")
    void doesNotMutateStoredBalances() {
        Balance creditor = new Balance(GROUP_ID, 1L, new BigDecimal("500.00"));
        Balance debtor = new Balance(GROUP_ID, 2L, new BigDecimal("-500.00"));
        when(balanceRepository.findByGroupId(GROUP_ID)).thenReturn(List.of(creditor, debtor));

        settlementService.getSettlements(GROUP_ID);

        // These are managed entities in production. If the engine drained them here, an
        // open transaction would flush the zeroed values straight into the balances table.
        assertEquals(new BigDecimal("500.00"), creditor.getNetBalance());
        assertEquals(new BigDecimal("-500.00"), debtor.getNetBalance());
    }
}
