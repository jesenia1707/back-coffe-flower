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

        private static final DateTimeFormatter FORMATO_PERIODO = DateTimeFormatter.ofPattern("MM/yyyy");

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

                NumberFormat formatoCLP = NumberFormat.getNumberInstance(
                                Locale.forLanguageTag("es-CL"));

                formatoCLP.setMaximumFractionDigits(0);
                formatoCLP.setMinimumFractionDigits(0);

                String totalFormateado = formatoCLP.format(totalVentas);
                String periodo = inicioMes.format(FORMATO_PERIODO);
                String fechaInicio = inicioMes.format(FORMATO_FECHA);
                String fechaFin = hoy.format(FORMATO_FECHA);

                String cuerpo = """
                                <!DOCTYPE html>
                                <html lang="es">
                                <head>
                                    <meta charset="UTF-8">
                                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                                    <title>Informe mensual Coffee Flower</title>
                                </head>
                                <body style="margin:0;padding:0;background-color:#f4eee7;
                                             font-family:Arial,Helvetica,sans-serif;color:#49352a;">

                                    <table role="presentation" width="100%%" cellspacing="0"
                                           cellpadding="0" border="0"
                                           style="background-color:#f4eee7;padding:30px 10px;">
                                        <tr>
                                            <td align="center">

                                                <table role="presentation" width="600" cellspacing="0"
                                                       cellpadding="0" border="0"
                                                       style="width:100%%;max-width:600px;background:#ffffff;
                                                              border-radius:14px;overflow:hidden;">

                                                    <tr>
                                                        <td style="background-color:#5c4033;padding:32px 24px;
                                                                   text-align:center;color:#ffffff;">
                                                            <div style="font-size:34px;margin-bottom:10px;">
                                                                &#9749;
                                                            </div>
                                                            <h1 style="margin:0;font-size:28px;letter-spacing:2px;">
                                                                COFFEE FLOWER
                                                            </h1>
                                                            <p style="margin:10px 0 0;font-size:14px;color:#ead9c9;">
                                                                El aroma de cada momento
                                                            </p>
                                                        </td>
                                                    </tr>

                                                    <tr>
                                                        <td style="padding:30px 24px 15px;text-align:center;">
                                                            <h2 style="margin:0;font-size:23px;color:#5c4033;">
                                                                Informe mensual de ventas
                                                            </h2>
                                                            <p style="margin:10px 0 0;font-size:14px;color:#857367;">
                                                                Período: %s
                                                            </p>
                                                        </td>
                                                    </tr>

                                                    <tr>
                                                        <td style="padding:15px 24px;">
                                                            <h3 style="margin:0 0 16px;font-size:16px;color:#5c4033;">
                                                                RESUMEN DEL MES
                                                            </h3>

                                                            <table role="presentation" width="100%%"
                                                                   cellspacing="0" cellpadding="0" border="0">
                                                                <tr>
                                                                    <td width="50%%" style="padding:6px;">
                                                                        <table role="presentation" width="100%%"
                                                                               cellspacing="0" cellpadding="0"
                                                                               style="background:#f8f2eb;border:1px solid #eadfd3;
                                                                                      border-radius:10px;">
                                                                            <tr>
                                                                                <td style="padding:20px 12px;text-align:center;">
                                                                                    <div style="font-size:25px;">&#128722;</div>
                                                                                    <p style="margin:10px 0 5px;font-size:12px;color:#806b5b;">
                                                                                        PEDIDOS REGISTRADOS
                                                                                    </p>
                                                                                    <strong style="font-size:28px;color:#5c4033;">
                                                                                        %d
                                                                                    </strong>
                                                                                </td>
                                                                            </tr>
                                                                        </table>
                                                                    </td>

                                                                    <td width="50%%" style="padding:6px;">
                                                                        <table role="presentation" width="100%%"
                                                                               cellspacing="0" cellpadding="0"
                                                                               style="background:#f8f2eb;border:1px solid #eadfd3;
                                                                                      border-radius:10px;">
                                                                            <tr>
                                                                                <td style="padding:20px 12px;text-align:center;">
                                                                                    <div style="font-size:25px;">&#128230;</div>
                                                                                    <p style="margin:10px 0 5px;font-size:12px;color:#806b5b;">
                                                                                        UNIDADES VENDIDAS
                                                                                    </p>
                                                                                    <strong style="font-size:28px;color:#5c4033;">
                                                                                        %d
                                                                                    </strong>
                                                                                </td>
                                                                            </tr>
                                                                        </table>
                                                                    </td>
                                                                </tr>
                                                            </table>
                                                        </td>
                                                    </tr>

                                                    <tr>
                                                        <td style="padding:10px 30px 25px;">
                                                            <table role="presentation" width="100%%"
                                                                   cellspacing="0" cellpadding="0"
                                                                   style="background:#5c4033;border-radius:12px;">
                                                                <tr>
                                                                    <td style="padding:25px 15px;text-align:center;color:#ffffff;">
                                                                        <p style="margin:0 0 10px;font-size:13px;
                                                                                  letter-spacing:1px;color:#ead9c9;">
                                                                            TOTAL DE VENTAS
                                                                        </p>
                                                                        <div style="font-size:32px;font-weight:bold;">
                                                                            $%s CLP
                                                                        </div>
                                                                        <p style="margin:10px 0 0;font-size:12px;color:#ead9c9;">
                                                                            Ventas registradas durante el período
                                                                        </p>
                                                                    </td>
                                                                </tr>
                                                            </table>
                                                        </td>
                                                    </tr>

                                                    <tr>
                                                        <td style="padding:0 30px 25px;">
                                                            <table role="presentation" width="100%%"
                                                                   cellspacing="0" cellpadding="0">
                                                                <tr>
                                                                    <td style="padding:12px 0;border-bottom:1px solid #eee5dc;
                                                                               font-size:14px;color:#806b5b;">
                                                                        Inicio del período
                                                                    </td>
                                                                    <td align="right"
                                                                        style="padding:12px 0;border-bottom:1px solid #eee5dc;
                                                                               font-size:14px;font-weight:bold;color:#49352a;">
                                                                        %s
                                                                    </td>
                                                                </tr>
                                                                <tr>
                                                                    <td style="padding:12px 0;font-size:14px;color:#806b5b;">
                                                                        Cierre del informe
                                                                    </td>
                                                                    <td align="right"
                                                                        style="padding:12px 0;font-size:14px;font-weight:bold;color:#49352a;">
                                                                        %s
                                                                    </td>
                                                                </tr>
                                                            </table>
                                                        </td>
                                                    </tr>

                                                    <tr>
                                                        <td style="background:#f8f2eb;padding:25px 20px;text-align:center;
                                                                   border-top:1px solid #eadfd3;">
                                                            <p style="margin:0 0 8px;font-size:14px;color:#5c4033;">
                                                                Gracias por confiar en Coffee Flower.
                                                            </p>
                                                            <p style="margin:0;font-size:13px;color:#806b5b;">
                                                                Equipo Coffee Flower &#9749;
                                                            </p>
                                                            <p style="margin:15px 0 0;font-size:10px;color:#a18f80;">
                                                                Informe generado automáticamente.
                                                            </p>
                                                        </td>
                                                    </tr>

                                                </table>
                                            </td>
                                        </tr>
                                    </table>
                                </body>
                                </html>
                                """
                                .formatted(
                                                periodo,
                                                pedidosMes.size(),
                                                totalUnidades,
                                                totalFormateado,
                                                fechaInicio,
                                                fechaFin);

                PublishRequest solicitud = PublishRequest.builder()
                                .topicArn(topicArn)
                                .subject("Coffee Flower - Informe mensual de ventas - " + periodo)
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
