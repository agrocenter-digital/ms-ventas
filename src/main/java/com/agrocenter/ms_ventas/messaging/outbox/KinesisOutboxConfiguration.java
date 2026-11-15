package com.agrocenter.ms_ventas.messaging.outbox;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kinesis.KinesisClient;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "agrocenter.streaming.enabled", havingValue = "true")
@RequiredArgsConstructor
public class KinesisOutboxConfiguration {
    @Bean(destroyMethod = "close")
    KinesisClient kinesisClient(@Value("${aws.region:us-east-1}") String region) {
        return KinesisClient.builder().region(Region.of(region))
                .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(10)))
                .build();
    }

    @Bean
    OutboxSchedule outboxSchedule(KinesisOutboxPublisher publisher) {
        return new OutboxSchedule(publisher);
    }

    @RequiredArgsConstructor
    static class OutboxSchedule {
        private final KinesisOutboxPublisher publisher;

        @Scheduled(fixedDelayString = "${agrocenter.streaming.poll-delay-ms:2000}")
        public void publicar() { publisher.publicarUno(); }
    }
}
