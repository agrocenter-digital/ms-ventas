package com.agrocenter.ms_ventas.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DtoValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void rechazaPedidoSinProductos() {
        assertThat(validator.validate(new CrearVentaRequest(List.of()))).isNotEmpty();
    }

    @Test
    void rechazaCantidadNoPositiva() {
        CrearVentaRequest request = new CrearVentaRequest(List.of(new ItemVentaRequest(1L, 0)));
        assertThat(validator.validate(request)).isNotEmpty();
    }

    @Test
    void rechazaProductoNuloOConIdNoPositivo() {
        CrearVentaRequest conItemNulo = new CrearVentaRequest(java.util.Arrays.asList((ItemVentaRequest) null));
        CrearVentaRequest conIdInvalido = new CrearVentaRequest(List.of(new ItemVentaRequest(0L, 1)));

        assertThat(validator.validate(conItemNulo)).isNotEmpty();
        assertThat(validator.validate(conIdInvalido)).isNotEmpty();
    }

    @Test
    void aceptaRequestValido() {
        CrearVentaRequest request = new CrearVentaRequest(List.of(new ItemVentaRequest(1L, 2)));
        assertThat(validator.validate(request)).isEmpty();
    }
}
