package com.coffeeflower.bff.service;

import com.coffeeflower.bff.model.PedidoEntity;
import com.coffeeflower.bff.model.PedidoItemEntity;
import com.coffeeflower.bff.repository.PedidoItemRepository;
import com.coffeeflower.bff.repository.PedidoRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class InformeComprasService {

        private static final ZoneId ZONA_CHILE = ZoneId.of("America/Santiago");

        private final PedidoRepository pedidoRepository;
        private final PedidoItemRepository itemRepository;
        private final JavaMailSender mailSender;

        @Value("${app.report.email}")
        private String correoDestino;

        @Value("${spring.mail.username}")
        private String correoRemitente;

        public InformeComprasService(
                        PedidoRepository pedidoRepository,
                        PedidoItemRepository itemRepository,
                        JavaMailSender mailSender) {

                this.pedidoRepository = pedidoRepository;
                this.itemRepository = itemRepository;
                this.mailSender = mailSender;
        }

        /**
         * Calcula las ventas del mes actual y envía
         * el informe directamente en el cuerpo del correo.
         */
        @Transactional(readOnly = true)
        public Map<String, Object> generarYEnviarInformeMensual()
                        throws Exception {

                LocalDate hoy = LocalDate.now(ZONA_CHILE);
                LocalDate inicioMes = hoy.withDayOfMonth(1);
                LocalDate inicioMesSiguiente = inicioMes.plusMonths(1);

                Instant desde = inicioMes
                                .atStartOfDay(ZONA_CHILE)
                                .toInstant();

                Instant hasta = inicioMesSiguiente
                                .atStartOfDay(ZONA_CHILE)
                                .toInstant();

                // Obtener los pedidos del mes actual.
                List<PedidoEntity> pedidosMes = pedidoRepository.findAll()
                                .stream()
                                .filter(pedido -> pedido.getCreadoEn() != null)
                                .filter(pedido -> {
                                        Instant fecha = pedido.getCreadoEn();

                                        return !fecha.isBefore(desde)
                                                        && fecha.isBefore(hasta);
                                })
                                .filter(pedido -> pedido.getEstado() == null
                                                || (!pedido.getEstado().equalsIgnoreCase("CANCELADO")
                                                                && !pedido.getEstado().equalsIgnoreCase("CANCELLED")))
                                .toList();

                // Calcular el total de ventas.
                BigDecimal totalVentas = pedidosMes.stream()
                                .map(PedidoEntity::getTotal)
                                .filter(total -> total != null)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                // Calcular las unidades vendidas.
                int totalUnidades = 0;

                for (PedidoEntity pedido : pedidosMes) {
                        List<PedidoItemEntity> items = itemRepository.findByPedidoId(pedido.getId());

                        totalUnidades += items.stream()
                                        .mapToInt(item -> item.getCantidad() == null
                                                        ? 0
                                                        : item.getCantidad())
                                        .sum();
                }

                String periodo = inicioMes.format(
                                DateTimeFormatter.ofPattern("MM/yyyy"));

                String totalFormateado = totalVentas
                                .stripTrailingZeros()
                                .toPlainString();

                // Construir el informe en texto.
                String cuerpo = """
                                COFFEE FLOWER
                                INFORME MENSUAL DE VENTAS

                                Periodo: %s al %s

                                RESUMEN DE VENTAS
                                --------------------------------
                                Pedidos registrados: %d
                                Unidades vendidas: %d
                                Total de ventas: $%s CLP

                                Este informe contiene los pedidos
                                registrados durante el mes.

                                Saludos,
                                Coffee Flower
                                """.formatted(
                                inicioMes,
                                hoy,
                                pedidosMes.size(),
                                totalUnidades,
                                totalFormateado);

                // Enviar el informe por correo.
                SimpleMailMessage mensaje = new SimpleMailMessage();

                mensaje.setFrom(correoRemitente);
                mensaje.setTo(correoDestino);
                mensaje.setSubject(
                                "Coffee Flower - Informe mensual de ventas - "
                                                + periodo);
                mensaje.setText(cuerpo);

                mailSender.send(mensaje);

                // Devolver los resultados al controlador.
                Map<String, Object> resultado = new HashMap<>();

                resultado.put(
                                "mensaje",
                                "Informe mensual enviado correctamente al correo.");
                resultado.put("periodo", periodo);
                resultado.put("cantidadPedidos", pedidosMes.size());
                resultado.put("unidadesVendidas", totalUnidades);
                resultado.put("totalVentas", totalVentas);
                resultado.put("correoDestino", correoDestino);

                return resultado;
        }

        /**
         * Compatibilidad con controladores que utilizan generarYEnviar().
         */
        @Transactional(readOnly = true)
        public int generarYEnviar() throws Exception {
                Map<String, Object> resultado = generarYEnviarInformeMensual();

                return ((Number) resultado.get("cantidadPedidos"))
                                .intValue();
        }
}