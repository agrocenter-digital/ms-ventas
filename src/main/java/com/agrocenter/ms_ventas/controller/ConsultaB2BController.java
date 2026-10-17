package com.agrocenter.ms_ventas.controller;

import com.agrocenter.ms_ventas.dto.ActualizarEstadoConsultaRequest;
import com.agrocenter.ms_ventas.dto.ConsultaB2BResponse;
import com.agrocenter.ms_ventas.dto.CrearConsultaB2BRequest;
import com.agrocenter.ms_ventas.entity.EstadoConsultaB2B;
import com.agrocenter.ms_ventas.service.ConsultaB2BService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/ventas/consultas", "/ventas/consultas"})
@Tag(name = "Consultas B2B", description = "Registro y gestion de consultas y cotizaciones B2B")
public class ConsultaB2BController {

    private final ConsultaB2BService consultaService;

    @PostMapping
    @Operation(summary = "Registrar una consulta o solicitud de cotizacion B2B")
    public ResponseEntity<ConsultaB2BResponse> crear(
            @Valid @RequestBody CrearConsultaB2BRequest request
    ) {
        ConsultaB2BResponse consulta = consultaService.crear(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(consulta.id())
                .toUri();
        return ResponseEntity.created(location).body(consulta);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Listar las consultas B2B", description = "Requiere rol ADMIN")
    public List<ConsultaB2BResponse> listar(
            @RequestParam(required = false) EstadoConsultaB2B estado
    ) {
        return consultaService.listar(estado);
    }

    @PatchMapping("/{id}/estado")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Actualizar el estado de una consulta B2B", description = "Requiere rol ADMIN")
    public ConsultaB2BResponse actualizarEstado(
            @PathVariable Long id,
            @Valid @RequestBody ActualizarEstadoConsultaRequest request
    ) {
        return consultaService.actualizarEstado(id, request.estado());
    }
}
