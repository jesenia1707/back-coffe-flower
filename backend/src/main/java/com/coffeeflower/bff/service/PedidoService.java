
package com.coffeeflower.bff.service;

import com.coffeeflower.bff.model.PedidoEntity;
import com.coffeeflower.bff.model.PedidoItemEntity;
import com.coffeeflower.bff.repository.PedidoRepository;
import com.coffeeflower.bff.repository.PedidoItemRepository;

import jakarta.transaction.Transactional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PedidoService {

    private static final Logger log =
            LoggerFactory.getLogger(PedidoService.class);

    private final PedidoRepository pedidoRepository;
    private final PedidoItemRepository pedidoItemRepository;
    private final PedidoSnsService pedidoSnsService;
    private final PedidoKafkaService pedidoKafkaService;

    public PedidoService(
            PedidoRepository pedidoRepository,
            PedidoItemRepository pedidoItemRepository,
            PedidoSnsService pedidoSnsService,
            PedidoKafkaService pedidoKafkaService) {

        this.pedidoRepository = pedidoRepository;
        this.pedidoItemRepository = pedidoItemRepository;
        this.pedidoSnsService = pedidoSnsService;
        this.pedidoKafkaService = pedidoKafkaService;
    }

    @Transactional
    public PedidoEntity crearPedido(
            String cliente,
            List<PedidoItemEntity> items,
            BigDecimal total) {

        if (cliente == null || cliente.isBlank()) {
            throw new IllegalArgumentException(
                    "El nombre del cliente es obligatorio");
        }

        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException(
                    "El pedido debe contener al menos un producto");
        }

        if (total == null || total.signum() < 0) {
            throw new IllegalArgumentException(
                    "El total del pedido no es válido");
        }

        PedidoEntity pedido = new PedidoEntity();
        pedido.setCliente(cliente.trim());
        pedido.setTotal(total);
        pedido.setEstado("RECIBIDO");

        PedidoEntity guardado =
                pedidoRepository.saveAndFlush(pedido);

        for (PedidoItemEntity item : items) {

            if (item == null) {
                throw new IllegalArgumentException(
                        "El pedido contiene un producto inválido");
            }

            item.setPedido(guardado);
        }

        List<PedidoItemEntity> itemsGuardados =
                pedidoItemRepository.saveAll(items);

        pedidoItemRepository.flush();

        // Construir el evento del pedido.
        Map<String, Object> eventoPedido =
                new LinkedHashMap<>();

        eventoPedido.put("id", guardado.getId());
        eventoPedido.put("cliente", guardado.getCliente());
        eventoPedido.put("total", guardado.getTotal());
        eventoPedido.put("estado", guardado.getEstado());
        eventoPedido.put("creadoEn", guardado.getCreadoEn());
        eventoPedido.put("origen", "coffee-flower");

        List<Map<String, Object>> productos =
                itemsGuardados.stream()
                        .map(item -> {
                            Map<String, Object> producto =
                                    new LinkedHashMap<>();

                            producto.put("productoId",
                                    item.getProductoId());
                            producto.put("nombre",
                                    item.getNombre());
                            producto.put("cantidad",
                                    item.getCantidad());
                            producto.put("precio",
                                    item.getPrecio());

                            return producto;
                        })
                        .toList();

        eventoPedido.put("items", productos);

        // Publicar solamente después de confirmar la transacción.
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {

                    @Override
                    public void afterCommit() {

                        // Publicación en Amazon SNS.
                        try {

                            pedidoSnsService.publicarPedido(
                                    eventoPedido);

                            log.info(
                                    "Evento SNS publicado para el pedido {}",
                                    guardado.getId());

                        } catch (Exception e) {

                            log.error(
                                    "Error publicando pedido {} en SNS",
                                    guardado.getId(),
                                    e);
                        }

                        // Publicación en Apache Kafka.
                        try {

                            pedidoKafkaService.publicarPedido(
                                    eventoPedido);

                            log.info(
                                    "Evento Kafka enviado para el pedido {}",
                                    guardado.getId());

                        } catch (Exception e) {

                            log.error(
                                    "Error publicando pedido {} en Kafka",
                                    guardado.getId(),
                                    e);
                        }
                    }
                });

        return guardado;
    }
}
