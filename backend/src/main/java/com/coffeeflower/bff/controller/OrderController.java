package com.coffeeflower.bff.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/pedidos")
public class OrderController {
    private final AtomicLong ids = new AtomicLong();
    private final List<OrderResponse> orders = new CopyOnWriteArrayList<>();

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@RequestBody OrderRequest request) {
        if (request.cliente() == null || request.cliente().isBlank() || request.items() == null || request.items().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El cliente y los productos son obligatorios");
        }
        if (request.total() == null || request.total().signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Total inválido");
        }
        OrderResponse order = new OrderResponse(ids.incrementAndGet(), request.cliente(), request.items(), request.total(), "RECIBIDO", Instant.now().toString());
        orders.add(order);
        return order;
    }

    @GetMapping
    public List<OrderResponse> list() { return List.copyOf(orders); }

    public record OrderItem(Object productoId, String nombre, int cantidad, BigDecimal precio) {}
    public record OrderRequest(String cliente, List<OrderItem> items, BigDecimal total) {}
    public record OrderResponse(long id, String cliente, List<OrderItem> items, BigDecimal total, String estado, String creadoEn) {}
}
