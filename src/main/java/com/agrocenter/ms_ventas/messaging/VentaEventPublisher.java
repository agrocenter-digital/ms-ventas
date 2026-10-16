package com.agrocenter.ms_ventas.messaging;

import com.agrocenter.ms_ventas.dto.VentaResponse;
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

    public void publicarVentaConfirmada(VentaResponse venta) {
        OrdenCompraEvent event = OrdenCompraEvent.desde(venta);
        kafkaTemplate.send(KafkaTopics.ORDENES_COMPRAS, String.valueOf(venta.id()), event)
                .whenComplete((result, exception) -> {
                    if (exception != null) {
                        log.error(
                                "No fue posible publicar el evento de venta ventaId={}",
                                venta.id(),
                                exception
                        );
                        return;
                    }
                    log.info(
                            "Evento de venta publicado ventaId={} topico={} particion={} offset={}",
                            venta.id(),
                            KafkaTopics.ORDENES_COMPRAS,
                            result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset()
                    );
                });
    }
}
