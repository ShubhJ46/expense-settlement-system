package com.project.Splitwise.kafka.config;

import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * What happens to a record the listener could not process.
 *
 * <p>The policy has two halves that matter independently: how many times to try again, and
 * which failures are not worth trying again at all.
 */
@Configuration
public class KafkaErrorHandlingConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaErrorHandlingConfig.class);

    /** Three attempts after the first, two seconds apart. */
    private static final long RETRY_INTERVAL_MS = 2_000L;
    private static final long MAX_RETRIES = 3L;

    @Bean
    public DefaultErrorHandler defaultErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                // Called once a record has exhausted its retries. Routes to <topic>.DLT on
                // the *same* partition number, which keeps a group's failures together and
                // makes the original ordering reconstructable when investigating.
                (record, exception) -> {
                    log.error("Routing to DLT after failure: topic={} partition={} offset={} key={} error={}",
                            record.topic(), record.partition(), record.offset(), record.key(),
                            exception.getMessage());

                    return new TopicPartition(record.topic() + ".DLT", record.partition());
                }
        );

        FixedBackOff backOff = new FixedBackOff(RETRY_INTERVAL_MS, MAX_RETRIES);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);

        // Failures that retrying cannot fix. A malformed event is just as malformed on the
        // fourth attempt, and because a consumer blocks its partition while it backs off,
        // retrying it would stall every well-formed event queued behind it for six seconds
        // before reaching the same conclusion.
        //
        // The inverse case is what is *absent* here: an optimistic-lock failure or a broker
        // hiccup is deliberately left retryable, because those succeed on a second look.
        errorHandler.addNotRetryableExceptions(
                IllegalArgumentException.class,
                NullPointerException.class
        );

        return errorHandler;
    }
}
