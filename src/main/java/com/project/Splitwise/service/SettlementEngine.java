package com.project.Splitwise.service;

import com.project.Splitwise.domain.settlement.Settlement;
import com.project.Splitwise.model.Balance;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

public class SettlementEngine {

    public static List<Settlement> settle(List<Balance> balances) {

        PriorityQueue<Balance> creditors =
                new PriorityQueue<>(
                        (a, b) -> b.getNetBalance().compareTo(a.getNetBalance())
                );

        PriorityQueue<Balance> debtors =
                new PriorityQueue<>(
                        Comparator.comparing(Balance::getNetBalance)
                );

        for (Balance b : balances) {
            if (b.getNetBalance().compareTo(BigDecimal.ZERO) > 0) {
                creditors.add(b);
            } else if (b.getNetBalance().compareTo(BigDecimal.ZERO) < 0) {
                debtors.add(b);
            }
        }

        List<Settlement> result = new ArrayList<>();

        while (!creditors.isEmpty() && !debtors.isEmpty()) {

            Balance creditor = creditors.poll();
            Balance debtor = debtors.poll();

            BigDecimal settleAmount =
                    creditor.getNetBalance()
                            .min(debtor.getNetBalance().negate());

            result.add(new Settlement(
                    debtor.getUserId(),
                    creditor.getUserId(),
                    settleAmount
            ));

            creditor.setNetBalance(
                    creditor.getNetBalance().subtract(settleAmount)
            );

            debtor.setNetBalance(
                    debtor.getNetBalance().add(settleAmount)
            );

            if (creditor.getNetBalance().compareTo(BigDecimal.ZERO) > 0) {
                creditors.add(creditor);
            }
            if (debtor.getNetBalance().compareTo(BigDecimal.ZERO) < 0) {
                debtors.add(debtor);
            }
        }

        return result;
    }
}
