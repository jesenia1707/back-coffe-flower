package com.coffeeflower.bff.service;

import com.coffeeflower.bff.model.PedidoEntity;
import com.coffeeflower.bff.model.PedidoItemEntity;
import com.coffeeflower.bff.repository.PedidoItemRepository;
import com.coffeeflower.bff.repository.PedidoRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class InformeComprasService {

        private static final ZoneId ZONA_CHILE = ZoneId.of("America/Santiago");

        private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        private final PedidoRepository pedidoRepository;
        private final PedidoItemRepository itemRepository;
        private final SnsClient snsClient;

        @Value("${AWS_SNS_TOPIC_ARN:${SNS_TOPIC_ARN:}}")
        private String topicArn;

        public InformeComprasService(
                        PedidoRepository pedidoRepository,
                        PedidoItemRepository itemRepository,
                        SnsClient snsClient) {

                this.pedidoRepository = pedidoRepository;
                this.itemRepository = itemRepository;
                this.snsClient = snsClient;
        }

        /**
         * Calcula las ventas del mes actual y publica
         * el informe mediante Amazon SNS.
         */
        @Transactional(readOnly = true)
        public Map<String, Object> generarYEnviarInformeMensual()
                        throws Exception {

                if (topicArn == null || topicArn.isBlank()) {
                        throw new IllegalStateException(
                                        "Falta configurar AWS_SNS_TOPIC_ARN.");
                }

                LocalDate hoy = LocalDate.now(ZONA_CHILE);
                LocalDate inicioMes = hoy.withDayOfMonth(1);
                LocalDate inicioMesSiguiente = inicioMes.plusMonths(1);

                Instant desde = inicioMes
                                .atStartOfDay(ZONA_CHILE)
                                .toInstant();

                Instant hasta = inicioMesSiguiente
                                .atStartOfDay(ZONA_CHILE)
                                .toInstant();

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

                BigDecimal totalVentas = pedidosMes.stream()
                                .map(PedidoEntity::getTotal)
                                .filter(total -> total != null)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                int totalUnidades = 0;

                for (PedidoEntity pedido : pedidosMes) {
                        List<PedidoItemEntity> items = itemRepository.findByPedidoId(pedido.getId());

                        totalUnidades += items.stream()
                                        .mapToInt(item -> item.getCantidad() == null
                                                        ? 0
                                                        : item.getCantidad())
                                        .sum();
                }

                NumberFormat formatoCLP = NumberFormat.getNumberInstance(Locale.forLanguageTag("es-CL"));

                formatoCLP.setMaximumFractionDigits(0);
                formatoCLP.setMinimumFractionDigits(0);

                String totalFormateado = formatoCLP.format(totalVentas);

                String periodo = inicioMes.format(
                                DateTimeFormatter.ofPattern("MM/yyyy"));

                String cuerpo = """
                                ☕ COFFEE FLOWER
                                INFORME MENSUAL DE VENTAS
                                ========================================

                                PERÍODO DEL INFORME
                                Desde: %s
                                Hasta: %s

                                RESUMEN DE VENTAS
                                ----------------------------------------
                                Pedidos registrados : %d
                                Unidades vendidas   : %d
                                Total de ventas     : $%s CLP

                                ========================================

                                Este informe contiene el resumen de los
                                pedidos registrados durante el período.

                                Gracias por confiar en Coffee Flower.

                                Equipo Coffee Flower
                                """.formatted(
                                inicioMes.format(FORMATO_FECHA),
                                hoy.format(FORMATO_FECHA),
                                pedidosMes.size(),
                                totalUnidades,
                                totalFormateado);

                PublishRequest solicitud = PublishRequest.builder()
                                .topicArn(topicArn)
                                .subject(
                                                "Coffee Flower - Informe mensual de ventas - "
                                                                + periodo)
                                .message(cuerpo)
                                .build();

                PublishResponse respuesta = snsClient.publish(solicitud);

                Map<String, Object> resultado = new HashMap<>();
                resultado.put(
                                "mensaje",
                                "Informe mensual publicado correctamente en Amazon SNS.");
                resultado.put("periodo", periodo);
                resultado.put("cantidadPedidos", pedidosMes.size());
                resultado.put("unidadesVendidas", totalUnidades);
                resultado.put("totalVentas", totalVentas);
                resultado.put("snsMessageId", respuesta.messageId());

                return resultado;
        }

        /**
         * Compatibilidad con controladores que usan generarYEnviar().
         */
        @Transactional(readOnly = true)
        public int generarYEnviar() throws Exception {
                Map<String, Object> resultado = generarYEnviarInformeMensual();

                return ((Number) resultado.get("cantidadPedidos")).intValue();
        }
}
