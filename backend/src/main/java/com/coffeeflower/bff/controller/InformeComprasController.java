package com.coffeeflower.bff.controller;

import com.coffeeflower.bff.service.InformeComprasService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;

import java.util.Map;

@RestController
@RequestMapping("/api/reportes")
public class InformeComprasController {

    private final InformeComprasService informeService;

    public InformeComprasController(
            InformeComprasService informeService) {
        this.informeService = informeService;
    }

    @PostMapping("/compras")
    public ResponseEntity<?> generarInforme() {
        try {
            int cantidad = informeService.generarYEnviar();

            return ResponseEntity.ok(Map.of(
                    "mensaje", "Informe PDF enviado correctamente al correo.",
                    "cantidadPedidos", cantidad));

        } catch (Exception e) {
            // El detalle queda en el servidor; no exponer
            // credenciales ni errores internos al navegador.
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "mensaje",
                            "No se pudo generar o enviar el informe."));
        }
    }
}