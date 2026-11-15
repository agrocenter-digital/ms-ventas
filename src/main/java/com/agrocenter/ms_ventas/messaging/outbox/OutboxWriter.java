package com.agrocenter.ms_ventas.messaging.outbox;

import com.agrocenter.ms_ventas.dto.VentaResponse;
import com.agrocenter.ms_ventas.entity.ConsultaB2B;
import com.agrocenter.ms_ventas.entity.EstadoConsultaB2B;
import com.agrocenter.ms_ventas.messaging.event.OrdenCompraEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.MDC;

@Component
@RequiredArgsConstructor
public class OutboxWriter {
    private final OutboxRepository repository;
    private final ObjectMapper mapper;

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void registrarVentaConfirmada(VentaResponse venta, String correlationId) {
        registrar("VentaConfirmada", "Venta", String.valueOf(venta.id()), "venta:" + venta.id(),
                correlationId, OrdenCompraEvent.desde(venta));
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void registrarConsultaCreada(ConsultaB2B consulta) {
        registrarConsulta("ConsultaB2BCreada", consulta, null);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void registrarConsultaEstadoActualizado(ConsultaB2B consulta, EstadoConsultaB2B anterior) {
        registrarConsulta("ConsultaB2BEstadoActualizado", consulta, anterior);
    }

    private void registrarConsulta(String tipo, ConsultaB2B consulta, EstadoConsultaB2B anterior) {
        var payload = new ConsultaB2BPayload(consulta.getId(), consulta.getSkuProducto(), consulta.getCantidad(),
                consulta.getOrigen().name(), anterior == null ? null : anterior.name(), consulta.getEstado().name());
        registrar(tipo, "ConsultaB2B", String.valueOf(consulta.getId()), "consulta:" + consulta.getId(),
                MDC.get("correlationId"), payload);
    }

    private void registrar(String tipo, String aggregateType, String aggregateId, String partitionKey,
                           String correlationId, Object payload) {
        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        var envelope = new EventEnvelope(id, tipo, 1, now, "ms-ventas", aggregateType, aggregateId, correlationId, payload);
        try {
            repository.save(new OutboxEvent(id, envelope.eventType(), envelope.aggregateId(),
                    partitionKey, mapper.writeValueAsString(envelope), now));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No fue posible serializar el evento", e);
        }
    }

    public record EventEnvelope(String eventId, String eventType, int schemaVersion, Instant occurredAt,
                                String producer, String aggregateType, String aggregateId, String correlationId,
                                Object payload) { }
    public record ConsultaB2BPayload(Long consultaId, String skuProducto, Integer cantidad, String origen,
                                     String estadoAnterior, String estadoActual) { }
}
