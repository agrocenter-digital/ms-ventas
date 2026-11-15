package com.agrocenter.ms_ventas.messaging;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.assertThat;
import com.agrocenter.ms_ventas.dto.VentaResponse;
import com.agrocenter.ms_ventas.messaging.audit.AuditoriaEventos;
import com.agrocenter.ms_ventas.messaging.event.OrdenCompraEvent;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class VentaEventPublisherSwitchTest {
    @Test void banderaDesactivadaNoEnviaYPorDefectoConservaKafka() {
        @SuppressWarnings("unchecked") KafkaTemplate<String, OrdenCompraEvent> kafka = mock(KafkaTemplate.class);
        var publisher = new VentaEventPublisher(kafka, mock(AuditoriaEventos.class));
        assertThat(ReflectionTestUtils.getField(publisher, "kafkaEnabled")).isEqualTo(true);
        var venta = new VentaResponse(1L, "cliente", null, null, null, null, null, List.of(), null, null);
        ReflectionTestUtils.setField(publisher, "kafkaEnabled", false);
        publisher.publicarVentaConfirmada(venta);
        verifyNoInteractions(kafka);
        ReflectionTestUtils.setField(publisher, "kafkaEnabled", true);
        when(kafka.send(anyString(), anyString(), any(OrdenCompraEvent.class))).thenReturn(new CompletableFuture<>());
        publisher.publicarVentaConfirmada(venta);
        verify(kafka).send(eq(KafkaTopics.ORDENES_COMPRAS), eq("1"), any(OrdenCompraEvent.class));
    }
}
