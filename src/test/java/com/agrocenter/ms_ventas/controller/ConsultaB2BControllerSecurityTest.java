package com.agrocenter.ms_ventas.controller;

import com.agrocenter.ms_ventas.config.SecurityConfig;
import com.agrocenter.ms_ventas.dto.ConsultaB2BResponse;
import com.agrocenter.ms_ventas.entity.EstadoConsultaB2B;
import com.agrocenter.ms_ventas.entity.OrigenConsultaB2B;
import com.agrocenter.ms_ventas.exception.GlobalExceptionHandler;
import com.agrocenter.ms_ventas.observability.CorrelationIdFilter;
import com.agrocenter.ms_ventas.security.RestAccessDeniedHandler;
import com.agrocenter.ms_ventas.security.RestAuthenticationEntryPoint;
import com.agrocenter.ms_ventas.security.SecurityErrorWriter;
import com.agrocenter.ms_ventas.service.ConsultaB2BService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ConsultaB2BController.class)
@Import({
        SecurityConfig.class,
        SecurityErrorWriter.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class,
        CorrelationIdFilter.class,
        GlobalExceptionHandler.class
})
class ConsultaB2BControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConsultaB2BService consultaService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private static final String PAYLOAD = """
            {
              "skuProducto": "MAY-001",
              "nombreProducto": "Big Bag urea",
              "cantidad": 10,
              "nombreCliente": "Camila Munoz",
              "correoContacto": "camila@agrocenter.cl",
              "mensaje": "Necesito cotizacion por volumen mayorista",
              "origen": "PRODUCTO_MAYORISTA"
            }
            """;

    @Test
    void anonimoPuedeCrearConsulta() throws Exception {
        when(consultaService.crear(any())).thenReturn(respuesta(5L));

        mockMvc.perform(post("/api/ventas/consultas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"));
    }

    @Test
    void rechazaConsultaInvalida() throws Exception {
        mockMvc.perform(post("/api/ventas/consultas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombreCliente":"A","correoContacto":"no-es-correo","mensaje":"corto","origen":null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rechazaListadoSinJwt() throws Exception {
        mockMvc.perform(get("/api/ventas/consultas"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void clienteNoPuedeListarConsultas() throws Exception {
        mockMvc.perform(get("/api/ventas/consultas")
                        .with(jwt().jwt(token -> token.subject("cliente-1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_CLIENTE"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void adminListaConsultas() throws Exception {
        when(consultaService.listar(any())).thenReturn(List.of(respuesta(1L)));

        mockMvc.perform(get("/api/ventas/consultas")
                        .with(jwt().jwt(token -> token.subject("admin-1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));
    }

    @Test
    void adminActualizaEstado() throws Exception {
        ConsultaB2BResponse respondida = new ConsultaB2BResponse(
                7L, null, null, 1, "Camila", "camila@agrocenter.cl", "Mensaje valido de prueba",
                OrigenConsultaB2B.CONTACTO_HOME, EstadoConsultaB2B.RESPONDIDO, Instant.parse("2026-08-28T12:00:00Z"));
        when(consultaService.actualizarEstado(anyLong(), any())).thenReturn(respondida);

        mockMvc.perform(patch("/api/ventas/consultas/7/estado")
                        .with(jwt().jwt(token -> token.subject("admin-1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estado\":\"RESPONDIDO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RESPONDIDO"));
    }

    private ConsultaB2BResponse respuesta(Long id) {
        return new ConsultaB2BResponse(
                id, "MAY-001", "Big Bag urea", 10, "Camila Munoz", "camila@agrocenter.cl",
                "Necesito cotizacion por volumen mayorista", OrigenConsultaB2B.PRODUCTO_MAYORISTA,
                EstadoConsultaB2B.PENDIENTE, Instant.parse("2026-08-28T12:00:00Z"));
    }
}
