package com.agrocenter.ms_ventas.controller;

import com.agrocenter.ms_ventas.dto.CrearVentaRequest;
import com.agrocenter.ms_ventas.dto.PaginaResponse;
import com.agrocenter.ms_ventas.dto.VentaCreationResult;
import com.agrocenter.ms_ventas.dto.VentaResponse;
import com.agrocenter.ms_ventas.exception.IdentidadInvalidaException;
import com.agrocenter.ms_ventas.observability.CorrelationIdFilter;
import com.agrocenter.ms_ventas.service.VentaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping({"/api/v1/ventas", "/api/ventas", "/api/ventas/", "/ventas", "/ventas/"})
@Tag(name = "Ventas", description = "Checkout e historial de ventas")
public class VentaController {

    private final VentaService ventaService;

    @PostMapping
    @PreAuthorize("hasRole('CLIENTE')")
    @Operation(summary = "Crear una venta idempotente")
    public ResponseEntity<VentaResponse> crear(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) String idempotencyKey,
            @Valid @RequestBody CrearVentaRequest request,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest
    ) {
        VentaCreationResult result = ventaService.crear(
                clienteId(jwt),
                jwt.getTokenValue(),
                idempotencyKey,
                CorrelationIdFilter.from(servletRequest),
                request
        );
        if (result.replay()) {
            return ResponseEntity.ok()
                    .header("Idempotent-Replay", "true")
                    .body(result.venta());
        }
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(result.venta().id())
                .toUri();
        return ResponseEntity.created(location).body(result.venta());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('CLIENTE', 'ADMIN')")
    @Operation(summary = "Consultar una venta; CLIENTE solo puede consultar una propia")
    public VentaResponse obtener(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt,
            Authentication authentication
    ) {
        return ventaService.obtener(id, clienteId(jwt), esAdmin(authentication));
    }

    @GetMapping("/mis-pedidos")
    @PreAuthorize("hasRole('CLIENTE')")
    @Operation(summary = "Listar las ventas del cliente autenticado")
    public PaginaResponse<VentaResponse> listarPropias(
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanio,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return ventaService.listarCliente(clienteId(jwt), pagina, tamanio);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Listar todas las ventas", description = "Requiere rol ADMIN")
    public PaginaResponse<VentaResponse> listarTodas(
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int tamanio
    ) {
        return ventaService.listarTodas(pagina, tamanio);
    }

    private boolean esAdmin(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }

    private String clienteId(Jwt jwt) {
        if (jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new IdentidadInvalidaException();
        }
        return jwt.getSubject();
    }
}
