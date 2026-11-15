package com.agrocenter.ms_ventas.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.PutRecordRequest;
import software.amazon.awssdk.services.kinesis.model.PutRecordResponse;

@ExtendWith(MockitoExtension.class)
class KinesisOutboxPublisherTest {
    @Mock OutboxRepository repository;
    @Mock KinesisClient client;
    KinesisOutboxPublisher publisher;
    OutboxEvent event;

    @BeforeEach
    void setUp() {
        publisher = new KinesisOutboxPublisher(repository, client);
        ReflectionTestUtils.setField(publisher, "stream", "agrocenter-kinesis-negocio");
        event = new OutboxEvent("id-1", "VentaConfirmada", "12", "venta:12", "{\"eventId\":\"id-1\"}", Instant.now());
        when(repository.buscarPublicables(any(), any()))
                .thenReturn(List.of(event));
    }

    @Test
    void marcaEnviadoSoloTrasConfirmacion() {
        when(client.putRecord(any(PutRecordRequest.class))).thenReturn(PutRecordResponse.builder().build());
        publisher.publicarUno();
        ArgumentCaptor<PutRecordRequest> request = ArgumentCaptor.forClass(PutRecordRequest.class);
        verify(client).putRecord(request.capture());
        assertThat(request.getValue().streamName()).isEqualTo("agrocenter-kinesis-negocio");
        assertThat(request.getValue().partitionKey()).isEqualTo("venta:12");
        assertThat(request.getValue().data().asUtf8String()).contains("id-1");
        assertThat(event.getSentAt()).isNotNull();
    }

    @Test
    void falloDeKinesisMantienePendienteYProgramaReintento() {
        when(client.putRecord(any(PutRecordRequest.class))).thenThrow(new IllegalStateException("sin conexion"));
        Instant before = Instant.now();
        publisher.publicarUno();
        assertThat(event.getSentAt()).isNull();
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getNextAttemptAt()).isAfter(before);
        assertThat(event.getLastError()).isEqualTo("IllegalStateException");
    }
}
