package com.project.Splitwise.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class BalanceId implements Serializable {
    private Long groupId;
    private Long userId;
}
