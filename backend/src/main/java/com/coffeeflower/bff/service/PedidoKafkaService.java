package com.coffeeflower.bff.service;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class PedidoKafkaService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topic.pedidos:pedidos}")
    private String topic;

    public PedidoKafkaService(
            KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publicarPedido(Map<String, Object> evento) {

        Object pedidoId = evento.get("id");

        if (pedidoId == null) {
            throw new IllegalArgumentException(
                    "El evento no contiene el ID del pedido");
        }

        kafkaTemplate.send(
                topic,
                pedidoId.toString(),
                evento
        ).whenComplete((resultado, error) -> {

            if (error != null) {
                System.err.println(
                        "Error publicando pedido en Kafka: "
                        + error.getMessage()
                );
            } else {
                System.out.println(
                        "Pedido " + pedidoId
                        + " publicado correctamente en Kafka"
                );
            }
        });
    }
}
