package com.agrocenter.ms_ventas.messaging.outbox;

import com.agrocenter.ms_ventas.dto.DetalleVentaResponse;
import com.agrocenter.ms_ventas.dto.VentaResponse;
import com.agrocenter.ms_ventas.entity.EstadoVenta;
import com.agrocenter.ms_ventas.entity.EstadoConsultaB2B;
import com.agrocenter.ms_ventas.entity.OrigenConsultaB2B;
import com.agrocenter.ms_ventas.dto.CrearConsultaB2BRequest;
import com.agrocenter.ms_ventas.service.ConsultaB2BService;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "CONTRATOS_DIR", matches = ".+")
class ExportarContratoTest {
    @Autowired OutboxWriter writer;
    @Autowired OutboxRepository repository;
    @Autowired ConsultaB2BService consultas;
    @MockitoBean JwtDecoder decoder;

    @Test @Transactional void exportaSobreRealDeVenta() throws Exception {
        var now = Instant.now();
        var item = new DetalleVentaResponse(1L, 10L, "SKU-TEST", "Producto", 2, new BigDecimal("1250.50"), new BigDecimal("2501.00"));
        var venta = new VentaResponse(42L, "cliente-test", now, EstadoVenta.CONFIRMADA, new BigDecimal("2501.00"),
                new BigDecimal("2501.00"), null, List.of(item), now, now);
        writer.registrarVentaConfirmada(venta, "contrato-test");
        var evento = repository.findAll().stream().filter(e -> "VentaConfirmada".equals(e.getEventType())).findFirst().orElseThrow();
        Files.writeString(Path.of(System.getenv("CONTRATOS_DIR"), "venta.json"), evento.getPayload());
        var consulta = consultas.crear(new CrearConsultaB2BRequest("SKU-TEST", "Producto", 2,
                "Nombre-privado", "privado@example.invalid", "Mensaje-privado", OrigenConsultaB2B.PRODUCTO_MAYORISTA));
        consultas.actualizarEstado(consulta.id(), EstadoConsultaB2B.RESPONDIDO);
        for (var par : List.of(new String[]{"ConsultaB2BCreada", "b2b-creada.json"}, new String[]{"ConsultaB2BEstadoActualizado", "b2b-cambio.json"})) {
            var b2b = repository.findAll().stream().filter(e -> par[0].equals(e.getEventType())).findFirst().orElseThrow();
            assertThat(b2b.getPayload()).doesNotContain("Nombre-privado", "privado@example.invalid", "Mensaje-privado");
            Files.writeString(Path.of(System.getenv("CONTRATOS_DIR"), par[1]), b2b.getPayload());
        }
    }
}
