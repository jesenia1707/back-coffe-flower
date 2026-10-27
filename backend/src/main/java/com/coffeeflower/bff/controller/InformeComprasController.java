package com.coffeeflower.bff.controller;

import com.coffeeflower.bff.service.InformeComprasService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/reportes")
public class InformeComprasController {

    private final InformeComprasService informeService;

    public InformeComprasController(
            InformeComprasService informeService) {
        this.informeService = informeService;
    }

    /**
     * Genera el PDF del mes actual y lo envía por correo.
     */
    @PostMapping({
            "/ventas-mensuales",
            "/compras"
    })
    public ResponseEntity<?> generarInformeMensual() {
        try {
            int cantidadPedidos = informeService.generarYEnviar();

            return ResponseEntity.ok(Map.of(
                    "mensaje",
                    "Informe mensual generado y enviado correctamente.",
                    "cantidadPedidos",
                    cantidadPedidos));

        } catch (Exception e) {
            e.printStackTrace();

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "mensaje",
                            "No se pudo generar o enviar el informe mensual."));
        }
    }
}