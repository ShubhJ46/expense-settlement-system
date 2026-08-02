package com.project.Splitwise.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** Composite key: an idempotency key is unique within a user, not globally. */
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyRecordId implements Serializable {
    private String idempotencyKey;
    private Long userId;
}
