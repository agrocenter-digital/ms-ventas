package com.agrocenter.ms_ventas.messaging.audit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class AuditoriaEventos {

    public static final String EMITIDO = "EMITIDO";
    public static final String CONSUMIDO = "CONSUMIDO";
    public static final String EXITO = "EXITO";
    public static final String REINTENTO = "REINTENTO";
    public static final String ERROR = "ERROR";

    private final int capacidad;
    private final String servicio;
    private final Deque<TrazaEvento> eventos = new ArrayDeque<>();
    private final AtomicLong secuencia = new AtomicLong();

    public AuditoriaEventos(
            @Value("${agrocenter.mensajeria.auditoria.capacidad:200}") int capacidad,
            @Value("${spring.application.name:microservicio}") String servicio
    ) {
        this.capacidad = Math.max(10, capacidad);
        this.servicio = servicio;
    }

    public synchronized void registrar(
            String topico,
            String direccion,
            String clave,
            String estado,
            String detalle
    ) {
        eventos.addFirst(new TrazaEvento(
                secuencia.incrementAndGet(),
                Instant.now(),
                servicio,
                topico,
                direccion,
                clave,
                estado,
                detalle
        ));
        while (eventos.size() > capacidad) {
            eventos.removeLast();
        }
    }

    public synchronized List<TrazaEvento> recientes(int limite) {
        int max = Math.max(1, Math.min(limite, capacidad));
        return eventos.stream().limit(max).toList();
    }
}
