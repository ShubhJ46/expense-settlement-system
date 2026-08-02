package com.project.Splitwise.model;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

/**
 * One participant's portion of one expense.
 *
 * <p>Shares are always stored explicitly, even for an even split. The split <em>type</em>
 * the caller asked for is resolved into concrete per-user amounts before anything is
 * persisted, so the record of who owed what is never a division to be recomputed later —
 * recomputation is where rounding drift creeps in.
 *
 * <p><strong>Invariant:</strong> the shares of an expense sum exactly to its amount. That is
 * enforced on the way in by {@link com.project.Splitwise.service.ExpenseService} and again
 * on the consuming side, where a violation is treated as a poison message.
 */
@Entity
@Table(name = "expense_shares")
@Data
public class ExpenseShare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long expenseId;
    private Long userId;

    /**
     * Scale 2 to match the monetary scale everywhere else; the allocator guarantees these
     * are exact at that scale rather than rounded to it.
     */
    @Column(precision = 19, scale = 2)
    private BigDecimal shareAmount;

}
