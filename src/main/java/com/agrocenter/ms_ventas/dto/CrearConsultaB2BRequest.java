package com.agrocenter.ms_ventas.dto;

import com.agrocenter.ms_ventas.entity.OrigenConsultaB2B;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CrearConsultaB2BRequest(
        @Size(max = 100) String skuProducto,
        @Size(max = 255) String nombreProducto,
        @Positive Integer cantidad,
        @NotBlank @Size(min = 2, max = 150) String nombreCliente,
        @NotBlank @Email @Size(max = 150) String correoContacto,
        @NotBlank @Size(min = 10, max = 4000) String mensaje,
        @NotNull OrigenConsultaB2B origen
) {
}
