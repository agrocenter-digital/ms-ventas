package com.agrocenter.ms_ventas.messaging.outbox;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.PutRecordRequest;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agrocenter.streaming.enabled", havingValue = "true")
public class KinesisOutboxPublisher {
    private final OutboxRepository repository;
    private final KinesisClient client;
    @Value("${agrocenter.streaming.commercial-stream}")
    private String stream;

    @Transactional
    public void publicarUno() {
        repository.buscarPublicables(Instant.now(), PageRequest.of(0, 1)).stream().findFirst()
                .ifPresent(event -> {
                    try {
                        client.putRecord(PutRecordRequest.builder().streamName(stream)
                                .partitionKey(event.getPartitionKey())
                                .data(SdkBytes.fromUtf8String(event.getPayload())).build());
                        event.enviado(Instant.now());
                        log.info("Evento publicado eventId={} stream={}", event.getEventId(), stream);
                    } catch (RuntimeException e) {
                        event.fallido(Instant.now(), e.getClass().getSimpleName());
                        log.warn("Error al publicar eventId={} intento={} tipo={}",
                                event.getEventId(), event.getAttempts(), e.getClass().getSimpleName());
                    }
                });
    }
}
