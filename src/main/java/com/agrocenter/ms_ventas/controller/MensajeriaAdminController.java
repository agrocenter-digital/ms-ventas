package com.agrocenter.ms_ventas.controller;

import com.agrocenter.ms_ventas.messaging.audit.AuditoriaEventos;
import com.agrocenter.ms_ventas.messaging.audit.TrazaEvento;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/ventas/admin/mensajeria", "/ventas/admin/mensajeria"})
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Mensajeria", description = "Trazabilidad de eventos de ventas")
public class MensajeriaAdminController {

    private final AuditoriaEventos auditoriaEventos;

    @GetMapping("/eventos")
    @Operation(summary = "Listar las trazas de eventos recientes emitidos por ms-ventas")
    public List<TrazaEvento> eventos(
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limite
    ) {
        return auditoriaEventos.recientes(limite);
    }
}
