package com.agrocenter.ms_ventas.service;

import com.agrocenter.ms_ventas.dto.ConsultaB2BResponse;
import com.agrocenter.ms_ventas.dto.CrearConsultaB2BRequest;
import com.agrocenter.ms_ventas.entity.ConsultaB2B;
import com.agrocenter.ms_ventas.entity.EstadoConsultaB2B;
import com.agrocenter.ms_ventas.exception.ConsultaB2BNoEncontradaException;
import com.agrocenter.ms_ventas.repository.ConsultaB2BRepository;
import com.agrocenter.ms_ventas.messaging.outbox.OutboxWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ConsultaB2BService {

    private final ConsultaB2BRepository consultaRepository;
    private final OutboxWriter outbox;

    @Transactional
    public ConsultaB2BResponse crear(CrearConsultaB2BRequest request) {
        ConsultaB2B consulta = new ConsultaB2B(
                normalizar(request.skuProducto()),
                normalizar(request.nombreProducto()),
                request.cantidad(),
                request.nombreCliente().trim(),
                request.correoContacto().trim(),
                request.mensaje().trim(),
                request.origen()
        );
        ConsultaB2B guardada = consultaRepository.save(consulta);
        outbox.registrarConsultaCreada(guardada);
        return ConsultaB2BResponse.desde(guardada);
    }

    @Transactional(readOnly = true)
    public List<ConsultaB2BResponse> listar(EstadoConsultaB2B estado) {
        List<ConsultaB2B> consultas = estado == null
                ? consultaRepository.findAllByOrderByFechaCreacionDesc()
                : consultaRepository.findByEstadoOrderByFechaCreacionDesc(estado);
        return consultas.stream().map(ConsultaB2BResponse::desde).toList();
    }

    @Transactional
    public ConsultaB2BResponse actualizarEstado(Long id, EstadoConsultaB2B estado) {
        ConsultaB2B consulta = consultaRepository.findByIdForUpdate(id)
                .orElseThrow(ConsultaB2BNoEncontradaException::new);
        EstadoConsultaB2B anterior = consulta.getEstado();
        if (anterior != estado) {
            consulta.actualizarEstado(estado);
            outbox.registrarConsultaEstadoActualizado(consulta, anterior);
        }
        return ConsultaB2BResponse.desde(consulta);
    }

    private String normalizar(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }
}
