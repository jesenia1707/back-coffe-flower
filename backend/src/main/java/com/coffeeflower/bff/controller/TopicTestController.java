package com.coffeeflower.bff.controller;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TopicTestController {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topic.pedidos:pedidos}")
    private String topic;

    public TopicTestController(
            KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @GetMapping("/topic-test")
    public ResponseEntity<Map<String, Object>> probarKafka(
            @RequestParam(defaultValue = "test") String msj) {

        Map<String, Object> evento = new LinkedHashMap<>();
        evento.put("mensaje", msj);
        evento.put("origen", "api");
        evento.put("fecha", Instant.now().toString());

        try {
            var resultado = kafkaTemplate.send(
                    topic,
                    "test-" + System.currentTimeMillis(),
                    evento
            ).get(10, TimeUnit.SECONDS);

            Map<String, Object> respuesta = new LinkedHashMap<>();
            respuesta.put("status", "enviado");
            respuesta.put("topico", resultado.getRecordMetadata().topic());
            respuesta.put("particion", resultado.getRecordMetadata().partition());
            respuesta.put("offset", resultado.getRecordMetadata().offset());

            return ResponseEntity.ok(respuesta);

        } catch (Exception e) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("status", "error");
            error.put("mensaje", e.getMessage());

            return ResponseEntity.internalServerError().body(error);
        }
    }
}
