package com.agrocenter.ms_ventas.messaging;

import com.agrocenter.ms_ventas.dto.VentaResponse;
import com.agrocenter.ms_ventas.messaging.audit.AuditoriaEventos;
import com.agrocenter.ms_ventas.messaging.event.OrdenCompraEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class VentaEventPublisher {

    private final KafkaTemplate<String, OrdenCompraEvent> kafkaTemplate;
    private final AuditoriaEventos auditoriaEventos;

    public void publicarVentaConfirmada(VentaResponse venta) {
        OrdenCompraEvent event = OrdenCompraEvent.desde(venta);
        String clave = String.valueOf(venta.id());
        try {
            kafkaTemplate.send(KafkaTopics.ORDENES_COMPRAS, clave, event)
                    .whenComplete((result, exception) -> {
                        if (exception != null) {
                            auditoriaEventos.registrar(
                                    KafkaTopics.ORDENES_COMPRAS,
                                    AuditoriaEventos.EMITIDO,
                                    clave,
                                    AuditoriaEventos.ERROR,
                                    "ventaId=" + venta.id() + " error=" + exception.getMessage()
                            );
                            log.error(
                                    "No fue posible publicar el evento de venta ventaId={}",
                                    venta.id(),
                                    exception
                            );
                            return;
                        }
                        auditoriaEventos.registrar(
                                KafkaTopics.ORDENES_COMPRAS,
                                AuditoriaEventos.EMITIDO,
                                clave,
                                AuditoriaEventos.EXITO,
                                "ventaId=" + venta.id()
                                        + " particion=" + result.getRecordMetadata().partition()
                                        + " offset=" + result.getRecordMetadata().offset()
                        );
                        log.info(
                                "Evento de venta publicado ventaId={} topico={} particion={} offset={}",
                                venta.id(),
                                KafkaTopics.ORDENES_COMPRAS,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset()
                        );
                    });
        } catch (RuntimeException exception) {
            auditoriaEventos.registrar(
                    KafkaTopics.ORDENES_COMPRAS,
                    AuditoriaEventos.EMITIDO,
                    clave,
                    AuditoriaEventos.ERROR,
                    "ventaId=" + venta.id() + " error=" + exception.getMessage()
            );
            throw exception;
        }
    }
}
