package com.project.Splitwise;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point.
 *
 * <p>{@code @EnableScheduling} is load-bearing rather than decorative: the outbox relay is a
 * scheduled poll, so without it events are staged in the database and never published, and
 * the symptom is writes that succeed while balances never move.
 *
 * <p>{@code @EntityScan} is explicit because entities are split across two packages by
 * design — {@code model} holds the write model, {@code readmodel} holds the projections.
 * Default scanning would find both, but naming them keeps the separation deliberate instead
 * of accidental.
 */
@SpringBootApplication
@EnableScheduling
@EntityScan(basePackages = {
        "com.project.Splitwise.model",
        "com.project.Splitwise.readmodel"
})
public class SplitwiseApplication {

	public static void main(String[] args) {
		SpringApplication.run(SplitwiseApplication.class, args);
	}

}
