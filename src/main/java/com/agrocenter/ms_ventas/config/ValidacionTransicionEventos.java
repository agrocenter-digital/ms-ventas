package com.agrocenter.ms_ventas.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** Rechaza dos publicadores activos para la misma venta antes de iniciar la aplicación. */
@Configuration
public class ValidacionTransicionEventos {
    @Bean
    static BeanFactoryPostProcessor validarTransicionEventos(Environment env) {
        return factory -> validar(env);
    }

    static void validar(Environment env) {
        boolean kinesis = env.getProperty("agrocenter.streaming.enabled", Boolean.class, false);
        boolean kafka = env.getProperty("KAFKA_PUBLISHER_ENABLED", Boolean.class, true);
        if (kinesis && kafka) throw new IllegalStateException("No activar publicadores Kafka y Kinesis simultaneamente en ventas");
    }
}
