package com.project.Splitwise.kafka.config;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaErrorHandlingConfig {

    @Bean
    public DefaultErrorHandler defaultErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> {
                    // 🔴 THIS IS THE RIGHT PLACE TO LOG
                    System.err.println(
                            "❌ Kafka message failed. " +
                                    "topic=" + record.topic() +
                                    ", partition=" + record.partition() +
                                    ", offset=" + record.offset() +
                                    ", key=" + record.key() +
                                    ", error=" + exception.getMessage()
                    );

                    return new TopicPartition(
                            record.topic() + ".DLT",
                            record.partition()
                    );
                }
        );

        //Retry 3 times with 2s delay
        FixedBackOff backOff = new FixedBackOff(2000L, 3L);
        DefaultErrorHandler errorHandler =
                new DefaultErrorHandler(recoverer, backOff);

        // DO NOT retry these (poison messages)
        errorHandler.addNotRetryableExceptions(
                IllegalArgumentException.class,
                NullPointerException.class
        );

        return errorHandler;
    }
}
