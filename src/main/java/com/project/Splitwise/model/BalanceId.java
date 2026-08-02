package com.project.Splitwise.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Composite key for {@link Balance}: a user holds one net position per group.
 *
 * <p>JPA requires an {@code @IdClass} to be serializable and to implement equals/hashCode,
 * which is what the Lombok annotations provide — without them the persistence context cannot
 * tell two references to the same row apart, and a load would not return the instance it
 * already holds.
 */
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class BalanceId implements Serializable {
    private Long groupId;
    private Long userId;
}
