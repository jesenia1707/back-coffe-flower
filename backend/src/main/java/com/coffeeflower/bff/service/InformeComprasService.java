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

import java.io.ByteArrayOutputStream;
import java.text.Normalizer;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class InformeComprasService {

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

        public int generarYEnviar() throws Exception {
                List<PedidoEntity> pedidos = pedidoRepository.findAll();

                byte[] pdf = generarPdf(pedidos);

                MimeMessage mensaje = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(mensaje, true, "UTF-8");

                helper.setFrom(correoRemitente);
                helper.setTo(correoDestino);
                helper.setSubject("Informe de compras - Coffee Flower");
                helper.setText(
                                "Adjuntamos el informe PDF con todas las compras registradas.",
                                false);
                helper.addAttachment("informe-compras-coffee-flower.pdf",
                                () -> new java.io.ByteArrayInputStream(pdf));

                mailSender.send(mensaje);
                return pedidos.size();
        }

        private byte[] generarPdf(List<PedidoEntity> pedidos)
                        throws Exception {

                try (PDDocument documento = new PDDocument();
                                ByteArrayOutputStream salida = new ByteArrayOutputStream()) {

                        PDPage pagina = new PDPage();
                        documento.addPage(pagina);

                        PDType1Font normal = new PDType1Font(
                                        Standard14Fonts.FontName.HELVETICA);
                        PDType1Font negrita = new PDType1Font(
                                        Standard14Fonts.FontName.HELVETICA_BOLD);

                        PDPageContentStream contenido = new PDPageContentStream(documento, pagina);

                        float y = 750;

                        contenido.beginText();
                        contenido.setFont(negrita, 18);
                        contenido.newLineAtOffset(50, y);
                        contenido.showText("Coffee Flower - Informe de compras");
                        contenido.endText();

                        y -= 30;
                        y = escribir(contenido, normal, 11, 50, y,
                                        "Total de pedidos: " + pedidos.size());

                        y -= 20;

                        DateTimeFormatter formato = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                                        .withZone(ZoneId.of("America/Santiago"));

                        for (PedidoEntity pedido : pedidos) {
                                if (y < 100) {
                                        contenido.close();
                                        pagina = new PDPage();
                                        documento.addPage(pagina);
                                        contenido = new PDPageContentStream(
                                                        documento, pagina);
                                        y = 750;
                                }

                                y = escribir(contenido, negrita, 12, 50, y,
                                                "Pedido #" + pedido.getId());

                                y = escribir(contenido, normal, 10, 60, y - 5,
                                                "Cliente: " + seguro(pedido.getCliente()));

                                y = escribir(contenido, normal, 10, 60, y,
                                                "Fecha: " + (pedido.getCreadoEn() == null
                                                                ? "Sin fecha"
                                                                : formato.format(pedido.getCreadoEn())));

                                y = escribir(contenido, normal, 10, 60, y,
                                                "Estado: " + seguro(pedido.getEstado()));

                                y = escribir(contenido, normal, 10, 60, y,
                                                "Total: $" + pedido.getTotal());

                                List<PedidoItemEntity> items = itemRepository.findByPedidoId(pedido.getId());

                                y = escribir(contenido, negrita, 10, 60, y,
                                                "Productos:");

                                for (PedidoItemEntity item : items) {
                                        y = escribir(contenido, normal, 9, 70, y,
                                                        seguro(item.getNombre())
                                                                        + " x" + item.getCantidad()
                                                                        + " - $" + item.getPrecio());
                                }

                                y -= 15;
                        }

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

                return y - tamano - 4;
        }

        private String seguro(String texto) {
                return texto == null || texto.isBlank()
                                ? "No informado"
                                : texto;
        }

        private String limpiar(String texto) {
                String normalizado = Normalizer.normalize(
                                texto, Normalizer.Form.NFD);
                return normalizado
                                .replaceAll("\\p{M}", "")
                                .replaceAll("[^\\x20-\\x7E]", "?");
        }
}