package com.agrocenter.ms_ventas.messaging.config;

import com.agrocenter.ms_ventas.messaging.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    NewTopic ordenesComprasTopic() {
        return TopicBuilder.name(KafkaTopics.ORDENES_COMPRAS)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
