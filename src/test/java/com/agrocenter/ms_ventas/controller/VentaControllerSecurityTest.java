package com.agrocenter.ms_ventas.controller;

import com.agrocenter.ms_ventas.config.SecurityConfig;
import com.agrocenter.ms_ventas.dto.PaginaResponse;
import com.agrocenter.ms_ventas.dto.VentaCreationResult;
import com.agrocenter.ms_ventas.dto.VentaResponse;
import com.agrocenter.ms_ventas.entity.EstadoVenta;
import com.agrocenter.ms_ventas.exception.AccesoVentaDenegadoException;
import com.agrocenter.ms_ventas.exception.GlobalExceptionHandler;
import com.agrocenter.ms_ventas.observability.CorrelationIdFilter;
import com.agrocenter.ms_ventas.security.RestAccessDeniedHandler;
import com.agrocenter.ms_ventas.security.RestAuthenticationEntryPoint;
import com.agrocenter.ms_ventas.security.SecurityErrorWriter;
import com.agrocenter.ms_ventas.service.VentaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VentaController.class)
@Import({
        SecurityConfig.class,
        SecurityErrorWriter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        CorrelationIdFilter.class,
        GlobalExceptionHandler.class
})
class VentaControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private VentaService ventaService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void rechazaRutaProtegidaSinJwt() throws Exception {
        mockMvc.perform(get("/api/v1/ventas"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andExpect(header().exists("X-Correlation-ID"));
    }

    @Test
    void clientePuedeCrearVenta() throws Exception {
        when(ventaService.crear(anyString(), anyString(), anyString(), anyString(), any()))
                .thenReturn(new VentaCreationResult(venta(11L, "cliente-1"), false));

        mockMvc.perform(post("/api/v1/ventas")
                        .with(jwt().jwt(token -> token.subject("cliente-1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CLIENTE")))
                        .header("Idempotency-Key", "checkout-11")
                        .header("X-Correlation-ID", "corr-test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items": [
                                    {"productoId": 10, "cantidad": 2}
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(11))
                .andExpect(jsonPath("$.clienteId").value("cliente-1"))
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"))
                .andExpect(header().string("X-Correlation-ID", "corr-test"));
    }

    @Test
    void clienteNoPuedeUsarRutaAdministrativa() throws Exception {
        mockMvc.perform(get("/api/v1/ventas")
                        .with(jwt().jwt(token -> token.subject("cliente-1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void adminPuedeListarTodasLasVentas() throws Exception {
        when(ventaService.listarTodas(anyInt(), anyInt()))
                .thenReturn(new PaginaResponse<>(List.of(), 0, 20, 0, 0));

        mockMvc.perform(get("/api/v1/ventas")
                        .with(jwt().jwt(token -> token.subject("admin-1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagina").value(0));
    }

    @Test
    void clienteNoPuedeConsultarVentaAjena() throws Exception {
        when(ventaService.obtener(anyLong(), anyString(), anyBoolean()))
                .thenThrow(new AccesoVentaDenegadoException());

        mockMvc.perform(get("/api/v1/ventas/99")
                        .with(jwt().jwt(token -> token.subject("cliente-1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SALE_ACCESS_DENIED"));
    }

    @Test
    void exigeClaveDeIdempotencia() throws Exception {
        mockMvc.perform(post("/api/v1/ventas")
                        .with(jwt().jwt(token -> token.subject("cliente-1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CLIENTE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"productoId\":10,\"cantidad\":2}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rechazaJwtSinSubjectConError401() throws Exception {
        mockMvc.perform(get("/api/v1/ventas/mis-pedidos")
                        .with(jwt().jwt(token -> token.claims(claims -> claims.remove("sub")))
                                .authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_IDENTITY"));
    }

    private VentaResponse venta(Long id, String clienteId) {
        Instant now = Instant.parse("2026-08-28T12:00:00Z");
        return new VentaResponse(
                id,
                clienteId,
                now,
                EstadoVenta.CONFIRMADA,
                new BigDecimal("49980.00"),
                new BigDecimal("49980.00"),
                null,
                List.of(),
                now,
                now
        );
    }
}
