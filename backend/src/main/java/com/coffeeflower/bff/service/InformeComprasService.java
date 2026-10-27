package com.coffeeflower.bff.service;

import com.coffeeflower.bff.model.PedidoEntity;
import com.coffeeflower.bff.model.PedidoItemEntity;
import com.coffeeflower.bff.repository.PedidoRepository;
import com.coffeeflower.bff.repository.PedidoItemRepository;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class InformeComprasService {

        private static final ZoneId ZONA = ZoneId.of("America/Santiago");

        private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                        .withZone(ZONA);

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
         * Calcula y envía el informe del mes actual.
         * Retorna la cantidad de pedidos incluidos.
         */
        public int generarYEnviar() throws Exception {

                YearMonth mesActual = YearMonth.now(ZONA);

                Instant inicio = mesActual
                                .atDay(1)
                                .atStartOfDay(ZONA)
                                .toInstant();

                Instant finExclusivo = mesActual
                                .plusMonths(1)
                                .atDay(1)
                                .atStartOfDay(ZONA)
                                .toInstant();

                List<PedidoEntity> pedidosMes = pedidoRepository.findAll().stream()
                                .filter(p -> p.getCreadoEn() != null)
                                .filter(p -> !p.getCreadoEn().isBefore(inicio)
                                                && p.getCreadoEn().isBefore(finExclusivo))
                                .filter(p -> p.getEstado() == null
                                                || (!p.getEstado().equalsIgnoreCase("CANCELADO")
                                                                && !p.getEstado().equalsIgnoreCase("CANCELLED")))
                                .toList();

                Map<String, ResumenProducto> resumen = new LinkedHashMap<>();

                BigDecimal totalVentas = BigDecimal.ZERO;
                int unidadesVendidas = 0;

                for (PedidoEntity pedido : pedidosMes) {

                        if (pedido.getTotal() != null) {
                                totalVentas = totalVentas.add(pedido.getTotal());
                        }

                        List<PedidoItemEntity> items = itemRepository.findByPedidoId(pedido.getId());

                        for (PedidoItemEntity item : items) {

                                int cantidad = item.getCantidad() == null
                                                ? 0
                                                : item.getCantidad();

                                BigDecimal precio = item.getPrecio() == null
                                                ? BigDecimal.ZERO
                                                : item.getPrecio();

                                String nombre = item.getNombre() == null
                                                ? "Producto sin nombre"
                                                : item.getNombre();

                                unidadesVendidas += cantidad;

                                ResumenProducto anterior = resumen.get(nombre);

                                BigDecimal importe = precio.multiply(
                                                BigDecimal.valueOf(cantidad));

                                if (anterior == null) {
                                        resumen.put(
                                                        nombre,
                                                        new ResumenProducto(cantidad, importe));
                                } else {
                                        resumen.put(
                                                        nombre,
                                                        new ResumenProducto(
                                                                        anterior.cantidad() + cantidad,
                                                                        anterior.importe().add(importe)));
                                }
                        }
                }

                byte[] pdf = generarPdf(
                                mesActual,
                                pedidosMes.size(),
                                unidadesVendidas,
                                totalVentas,
                                resumen);

                if (correoDestino == null || correoDestino.isBlank()) {
                        throw new IllegalStateException(
                                        "Falta configurar app.report.email.");
                }

                if (correoRemitente == null || correoRemitente.isBlank()) {
                        throw new IllegalStateException(
                                        "Falta configurar spring.mail.username.");
                }

                MimeMessage mensaje = mailSender.createMimeMessage();

                MimeMessageHelper helper = new MimeMessageHelper(
                                mensaje,
                                true,
                                "UTF-8");

                String nombreMes = mesActual.getMonth()
                                .getDisplayName(
                                                TextStyle.FULL,
                                                new Locale("es", "CL"));

                helper.setFrom(correoRemitente);
                helper.setTo(correoDestino);

                helper.setSubject(
                                "Informe mensual de ventas - Coffee Flower - "
                                                + nombreMes + " " + mesActual.getYear());

                String cuerpo = "Se ha generado el informe mensual de ventas "
                                + "de Coffee Flower.\n\n"
                                + "Período: "
                                + mesActual.atDay(1).format(FECHA)
                                + " al "
                                + mesActual.atEndOfMonth().format(FECHA)
                                + "\n"
                                + "Pedidos incluidos: "
                                + pedidosMes.size()
                                + "\n"
                                + "Total de productos vendidos: "
                                + unidadesVendidas
                                + " unidades\n"
                                + "Importe total de pedidos: $"
                                + totalVentas.toPlainString()
                                + "\n\n"
                                + "Adjuntamos el informe detallado en PDF.\n\n"
                                + "Saludos,\nCoffee Flower";

                helper.setText(cuerpo, false);

                helper.addAttachment(
                                "informe-ventas-" + mesActual
                                                + "-coffee-flower.pdf",
                                () -> new ByteArrayInputStream(pdf));

                mailSender.send(mensaje);

                return pedidosMes.size();
        }

        private byte[] generarPdf(
                        YearMonth mes,
                        int cantidadPedidos,
                        int unidadesVendidas,
                        BigDecimal totalVentas,
                        Map<String, ResumenProducto> resumen) throws Exception {

                try (
                                PDDocument documento = new PDDocument();
                                ByteArrayOutputStream salida = new ByteArrayOutputStream()) {

                        PDType1Font normal = new PDType1Font(
                                        Standard14Fonts.FontName.HELVETICA);

                        PDType1Font negrita = new PDType1Font(
                                        Standard14Fonts.FontName.HELVETICA_BOLD);

                        PDPage pagina = new PDPage();
                        documento.addPage(pagina);

                        PDPageContentStream contenido = new PDPageContentStream(documento, pagina);

                        float y = 750;

                        y = escribir(
                                        contenido, negrita, 18, 50, y,
                                        "Coffee Flower - Informe mensual de ventas");

                        y -= 8;

                        y = escribir(
                                        contenido, normal, 11, 50, y,
                                        "Periodo: "
                                                        + mes.atDay(1).format(FECHA)
                                                        + " al "
                                                        + mes.atEndOfMonth().format(FECHA));

                        y -= 10;

                        y = escribir(
                                        contenido, negrita, 12, 50, y,
                                        "Resumen de ventas");

                        y = escribir(
                                        contenido, normal, 11, 50, y,
                                        "Pedidos incluidos: " + cantidadPedidos);

                        y = escribir(
                                        contenido, normal, 11, 50, y,
                                        "Total de productos vendidos: "
                                                        + unidadesVendidas + " unidades");

                        y = escribir(
                                        contenido, normal, 11, 50, y,
                                        "Importe total de pedidos: $"
                                                        + totalVentas.toPlainString());

                        y -= 15;

                        y = escribir(
                                        contenido, negrita, 12, 50, y,
                                        "Detalle de productos vendidos");

                        y = escribir(
                                        contenido, negrita, 10, 50, y,
                                        "Producto - Unidades - Importe");

                        if (resumen.isEmpty()) {
                                y = escribir(
                                                contenido, normal, 10, 50, y,
                                                "No hay productos vendidos registrados para este mes.");
                        } else {
                                for (Map.Entry<String, ResumenProducto> entrada : resumen.entrySet()) {

                                        if (y < 70) {
                                                contenido.close();

                                                pagina = new PDPage();
                                                documento.addPage(pagina);

                                                contenido = new PDPageContentStream(
                                                                documento,
                                                                pagina);

                                                y = 750;
                                        }

                                        String nombre = entrada.getKey();
                                        ResumenProducto dato = entrada.getValue();

                                        y = escribir(
                                                        contenido, normal, 9, 50, y,
                                                        limitar(nombre, 28)
                                                                        + " - "
                                                                        + dato.cantidad()
                                                                        + " unidades - $"
                                                                        + dato.importe().toPlainString());
                                }
                        }

                        y -= 20;

                        if (y < 60) {
                                contenido.close();

                                pagina = new PDPage();
                                documento.addPage(pagina);

                                contenido = new PDPageContentStream(
                                                documento,
                                                pagina);

                                y = 750;
                        }

                        escribir(
                                        contenido, normal, 9, 50, y,
                                        "Generado el "
                                                        + FECHA_HORA.format(Instant.now()));

                        contenido.close();

                        documento.save(salida);

                        return salida.toByteArray();
                }
        }

        private float escribir(
                        PDPageContentStream contenido,
                        PDType1Font fuente,
                        int tamano,
                        float x,
                        float y,
                        String texto) throws Exception {

                contenido.beginText();
                contenido.setFont(fuente, tamano);
                contenido.newLineAtOffset(x, y);
                contenido.showText(limpiar(texto));
                contenido.endText();

                return y - tamano - 5;
        }

        private String limitar(String texto, int max) {
                return texto.length() <= max
                                ? texto
                                : texto.substring(0, max - 3) + "...";
        }

        private String limpiar(String texto) {
                return Normalizer.normalize(
                                texto,
                                Normalizer.Form.NFD)
                                .replaceAll("\\p{M}", "")
                                .replaceAll("[^\\x20-\\x7E]", "?");
        }

        private record ResumenProducto(
                        int cantidad,
                        BigDecimal importe) {
        }
}