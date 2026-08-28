package com.agrocenter.ms_ventas.service;

import com.agrocenter.ms_ventas.dto.VentaResponse;

record IdempotencyRecord(VentaResponse venta, String requestHash) {
}
