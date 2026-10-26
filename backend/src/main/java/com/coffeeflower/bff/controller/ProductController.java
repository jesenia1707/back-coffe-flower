
package com.coffeeflower.bff.controller;

import com.coffeeflower.bff.model.Product;
import com.coffeeflower.bff.model.ProductEntity;
import com.coffeeflower.bff.repository.ProductRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/productos")
public class ProductController {

    private final ProductRepository productRepository;

    public ProductController(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @GetMapping("/health")
    public Health health() {
        return new Health("UP", "Coffee Flower BFF");
    }

    @GetMapping
    public List<Product> getProducts() {
        return productRepository.findAll()
                .stream()
                .map(this::toProduct)
                .toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Product createProduct(@RequestBody ProductInput input) {
        if (input.nombre() == null || input.nombre().isBlank()
                || input.precio() == null
                || input.precio().signum() < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Nombre y precio válido son obligatorios"
            );
        }

        ProductEntity entity = new ProductEntity();
        entity.setNombre(input.nombre());
        entity.setDescripcion(
                input.descripcion() == null ? "" : input.descripcion()
        );
        entity.setPrecio(input.precio());
        entity.setCategoria(
                input.categoria() == null ? "Café" : input.categoria()
        );
        entity.setEmoji(
                input.emoji() == null ? "☕" : input.emoji()
        );

        ProductEntity saved = productRepository.save(entity);
        return toProduct(saved);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProduct(@PathVariable Long id) {
        if (!productRepository.existsById(id)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Producto no encontrado"
            );
        }

        productRepository.deleteById(id);
    }

    private Product toProduct(ProductEntity entity) {
        return new Product(
                entity.getId(),
                entity.getNombre(),
                entity.getDescripcion(),
                entity.getPrecio(),
                entity.getCategoria(),
                entity.getEmoji()
        );
    }

    public record Health(String status, String service) {}

    public record ProductInput(
            String nombre,
            String descripcion,
            BigDecimal precio,
            String categoria,
            String emoji
    ) {}
}
