package com.project.Splitwise.domain.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventEnvelope<T> {

    private String eventType;   //ExpenseCreated
    private int version;        // 1, 2, ...
    private T payload;
}
