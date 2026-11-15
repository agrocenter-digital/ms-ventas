package com.agrocenter.ms_ventas.config;

import static org.assertj.core.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

class ValidacionTransicionEventosTest {
    @Test void admiteKafkaActualYKinesisSoloSiKafkaEstaDesactivado() {
        assertThatCode(() -> ValidacionTransicionEventos.validar(new MockEnvironment())).doesNotThrowAnyException();
        var env = new MockEnvironment().withProperty("agrocenter.streaming.enabled", "true");
        assertThatThrownBy(() -> ValidacionTransicionEventos.validar(env)).isInstanceOf(IllegalStateException.class);
        env.setProperty("KAFKA_PUBLISHER_ENABLED", "false");
        assertThatCode(() -> ValidacionTransicionEventos.validar(env)).doesNotThrowAnyException();
    }
    @Test void fallaElArranqueConConfiguracionConflictiva() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                    "agrocenter.streaming.enabled", "true", "KAFKA_PUBLISHER_ENABLED", "true")));
            context.register(ValidacionTransicionEventos.class);
            assertThatThrownBy(context::refresh).isInstanceOf(IllegalStateException.class);
        }
    }
}
