package com.agrocenter.ms_ventas.service;

import com.agrocenter.ms_ventas.dto.ConsultaB2BResponse;
import com.agrocenter.ms_ventas.dto.CrearConsultaB2BRequest;
import com.agrocenter.ms_ventas.entity.EstadoConsultaB2B;
import com.agrocenter.ms_ventas.entity.OrigenConsultaB2B;
import com.agrocenter.ms_ventas.exception.ConsultaB2BNoEncontradaException;
import com.agrocenter.ms_ventas.repository.ConsultaB2BRepository;
import com.agrocenter.ms_ventas.messaging.outbox.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.MDC;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class ConsultaB2BServiceIntegrationTest {

    @Autowired
    private ConsultaB2BService consultaService;

    @Autowired
    private ConsultaB2BRepository consultaRepository;

    @Autowired private OutboxRepository outbox;
    @Autowired private TransactionTemplate transactions;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void cleanDatabase() {
        outbox.deleteAll();
        consultaRepository.deleteAll();
    }

    @Test
    void eventosB2bRespetanTransicionesYExcluyenDatosPersonales() {
        MDC.put("correlationId", "b2b-test-1");
        try {
            var creada = consultaService.crear(consulta("Contacto", OrigenConsultaB2B.CONTACTO_HOME));
            consultaService.actualizarEstado(creada.id(), EstadoConsultaB2B.RESPONDIDO);
            consultaService.actualizarEstado(creada.id(), EstadoConsultaB2B.RESPONDIDO);
            var eventos = outbox.findAll();
            assertThat(eventos).hasSize(2);
            assertThat(eventos).extracting(e -> e.getEventType())
                    .containsExactlyInAnyOrder("ConsultaB2BCreada", "ConsultaB2BEstadoActualizado");
            assertThat(eventos).allSatisfy(e -> {
                assertThat(e.getPartitionKey()).isEqualTo("consulta:" + creada.id());
                assertThat(e.getPayload()).contains("b2b-test-1", "consultaId")
                        .doesNotContain("contacto@agrocenter.cl", "Mensaje de consulta", "nombreCliente", "correoContacto");
            });
            assertThat(eventos.stream().filter(e -> e.getEventType().equals("ConsultaB2BEstadoActualizado"))
                    .findFirst().orElseThrow().getPayload()).contains("PENDIENTE", "RESPONDIDO");
        } finally { MDC.remove("correlationId"); }
    }

    @Test
    void rollbackRevierteConsultaYOutbox() {
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            consultaService.crear(consulta("Rollback", OrigenConsultaB2B.CONTACTO_HOME));
            throw new IllegalStateException("rollback simulado");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(consultaRepository.count()).isZero();
        assertThat(outbox.count()).isZero();
    }

    @Test
    void rollbackRevierteCambioDeEstadoYEvento() {
        var creada = consultaService.crear(consulta("Rollback", OrigenConsultaB2B.CONTACTO_HOME));
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            consultaService.actualizarEstado(creada.id(), EstadoConsultaB2B.RESPONDIDO);
            throw new IllegalStateException("rollback simulado");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(consultaRepository.findById(creada.id()).orElseThrow().getEstado()).isEqualTo(EstadoConsultaB2B.PENDIENTE);
        assertThat(outbox.count()).isEqualTo(1);
    }

    @Test
    void cambiosSimultaneosAlMismoEstadoProducenUnSoloEvento() throws Exception {
        var creada = consultaService.crear(consulta("Concurrente", OrigenConsultaB2B.CONTACTO_HOME));
        var inicio = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { inicio.await(); consultaService.actualizarEstado(creada.id(), EstadoConsultaB2B.RESPONDIDO); return null; });
            var b = executor.submit(() -> { inicio.await(); consultaService.actualizarEstado(creada.id(), EstadoConsultaB2B.RESPONDIDO); return null; });
            inicio.countDown();
            a.get(20, TimeUnit.SECONDS);
            b.get(20, TimeUnit.SECONDS);
        }
        assertThat(outbox.findAll()).extracting(e -> e.getEventType())
                .containsExactlyInAnyOrder("ConsultaB2BCreada", "ConsultaB2BEstadoActualizado");
    }

    @Test
    void persisteConsultaComoPendienteConValoresPorDefecto() {
        ConsultaB2BResponse creada = consultaService.crear(new CrearConsultaB2BRequest(
                "MAY-001",
                "Big Bag de urea",
                null,
                "Camila Muñoz",
                "camila@agrocenter.cl",
                "Necesito 10 toneladas para la temporada",
                OrigenConsultaB2B.PRODUCTO_MAYORISTA
        ));

        assertThat(creada.id()).isNotNull();
        assertThat(creada.estado()).isEqualTo(EstadoConsultaB2B.PENDIENTE);
        assertThat(creada.cantidad()).isEqualTo(1);
        assertThat(creada.fechaCreacion()).isNotNull();
        assertThat(consultaRepository.findById(creada.id())).isPresent();
    }

    @Test
    void listaTodasYFiltraPorEstado() {
        consultaService.crear(consulta("Contacto", OrigenConsultaB2B.CONTACTO_HOME));
        ConsultaB2BResponse producto = consultaService.crear(consulta("Mayorista", OrigenConsultaB2B.PRODUCTO_MAYORISTA));
        consultaService.actualizarEstado(producto.id(), EstadoConsultaB2B.RESPONDIDO);

        List<ConsultaB2BResponse> todas = consultaService.listar(null);
        List<ConsultaB2BResponse> pendientes = consultaService.listar(EstadoConsultaB2B.PENDIENTE);
        List<ConsultaB2BResponse> respondidas = consultaService.listar(EstadoConsultaB2B.RESPONDIDO);

        assertThat(todas).hasSize(2);
        assertThat(pendientes).singleElement().satisfies(c ->
                assertThat(c.origen()).isEqualTo(OrigenConsultaB2B.CONTACTO_HOME));
        assertThat(respondidas).singleElement().satisfies(c ->
                assertThat(c.id()).isEqualTo(producto.id()));
    }

    @Test
    void actualizarEstadoFallaSiNoExiste() {
        assertThatThrownBy(() -> consultaService.actualizarEstado(9999L, EstadoConsultaB2B.ARCHIVADO))
                .isInstanceOf(ConsultaB2BNoEncontradaException.class);
    }

    private CrearConsultaB2BRequest consulta(String nombre, OrigenConsultaB2B origen) {
        return new CrearConsultaB2BRequest(
                null,
                null,
                1,
                nombre,
                nombre.toLowerCase() + "@agrocenter.cl",
                "Mensaje de consulta valido para la prueba",
                origen
        );
    }
}
