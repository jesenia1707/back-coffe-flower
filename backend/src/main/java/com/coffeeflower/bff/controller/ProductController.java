package com.coffeeflower.bff.controller;

import com.coffeeflower.bff.model.Product;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api")
public class ProductController {
    private final AtomicLong ids = new AtomicLong(6);
    private final List<Product> products = new CopyOnWriteArrayList<>(List.of(
        new Product(1L, "Latte Vainilla", "Espresso, leche cremosa y un toque de vainilla.", new BigDecimal("3490"), "Café", "☕"),
        new Product(2L, "Cappuccino", "Espresso intenso con espuma de leche.", new BigDecimal("2990"), "Café", "🥛"),
        new Product(3L, "Croissant de mantequilla", "Hojaldre dorado, horneado cada mañana.", new BigDecimal("2290"), "Pastelería", "🥐"),
        new Product(4L, "Torta de chocolate", "Bizcocho húmedo con ganache de chocolate.", new BigDecimal("3990"), "Pastelería", "🍰"),
        new Product(5L, "Cold Brew", "Café de extracción lenta, suave y refrescante.", new BigDecimal("3290"), "Café", "🧋"),
        new Product(6L, "Roll de canela", "Masa suave, canela y glaseado delicado.", new BigDecimal("2590"), "Pastelería", "🍥")
    ));

    @GetMapping("/health")
    public Health health() { return new Health("UP", "Coffee Flower BFF"); }

    @GetMapping("/productos")
    public List<Product> getProducts() { return List.copyOf(products); }

    @PostMapping("/productos")
    @ResponseStatus(HttpStatus.CREATED)
    public Product createProduct(@RequestBody ProductInput input) {
        if (input.nombre() == null || input.nombre().isBlank() || input.precio() == null || input.precio().signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nombre y precio válido son obligatorios");
        }
        Product p = new Product(ids.incrementAndGet(), input.nombre(), input.descripcion() == null ? "" : input.descripcion(),
            input.precio(), input.categoria() == null ? "Café" : input.categoria(), input.emoji() == null ? "☕" : input.emoji());
        products.add(p);
        return p;
    }

    @DeleteMapping("/productos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProduct(@PathVariable Long id) {
        boolean removed = products.removeIf(p -> p.id().equals(id));
        if (!removed) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Producto no encontrado");
    }

    public record Health(String status, String service) {}
    public record ProductInput(String nombre, String descripcion, BigDecimal precio, String categoria, String emoji) {}
}
