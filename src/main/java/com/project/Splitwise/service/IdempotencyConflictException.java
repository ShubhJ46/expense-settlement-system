package com.project.Splitwise.service;

/**
 * The same idempotency key was reused with a different request body.
 *
 * <p>Distinct from a replay: a replay is the client doing exactly what it is supposed to do
 * after a timeout, and is answered with the original resource. This is the client reusing a
 * key it should have rotated, and answering it with the first response would silently drop
 * the second request. Surfaces as 409.
 */
public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String message) {
        super(message);
    }
}
