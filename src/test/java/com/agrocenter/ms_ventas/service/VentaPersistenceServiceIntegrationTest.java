package com.agrocenter.ms_ventas.service;

import com.agrocenter.ms_ventas.dto.VentaResponse;
import com.agrocenter.ms_ventas.entity.EstadoVenta;
import com.agrocenter.ms_ventas.repository.VentaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class VentaPersistenceServiceIntegrationTest {

    @Autowired
    private VentaPersistenceService persistenceService;

    @Autowired
    private VentaRepository ventaRepository;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void cleanDatabase() {
        ventaRepository.deleteAll();
    }

    @Test
    void persisteSnapshotsYTransicionDeEstado() {
        VentaResponse pendiente = persistenceService.crearPendiente(
                "cliente-1",
                "key-1",
                "hash-1",
                List.of(new DetallePreparado(
                        10L,
                        "SEM-010",
                        "Semilla historica",
                        2,
                        new BigDecimal("1250.50"),
                        new BigDecimal("2501.00")
                )),
                new BigDecimal("2501.00"),
                new BigDecimal("2501.00")
        );

        VentaResponse confirmada = persistenceService.confirmar(pendiente.id());
        VentaResponse recargada = persistenceService.obtener(pendiente.id());

        assertThat(confirmada.estado()).isEqualTo(EstadoVenta.CONFIRMADA);
        assertThat(recargada.items()).singleElement().satisfies(item -> {
            assertThat(item.sku()).isEqualTo("SEM-010");
            assertThat(item.nombreProducto()).isEqualTo("Semilla historica");
            assertThat(item.precioUnitario()).isEqualByComparingTo("1250.50");
        });
    }

    @Test
    void impideDosVentasConLaMismaClavePorCliente() {
        crearVenta("cliente-1", "key-repetida");

        assertThatThrownBy(() -> crearVenta("cliente-1", "key-repetida"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void crearVenta(String clienteId, String key) {
        persistenceService.crearPendiente(
                clienteId,
                key,
                "hash",
                List.of(new DetallePreparado(
                        10L,
                        "SEM-010",
                        "Semilla",
                        1,
                        new BigDecimal("100.00"),
                        new BigDecimal("100.00")
                )),
                new BigDecimal("100.00"),
                new BigDecimal("100.00")
        );
    }
}
