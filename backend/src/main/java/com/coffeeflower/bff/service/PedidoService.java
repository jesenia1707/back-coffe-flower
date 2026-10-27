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
import java.util.List;

@Service
public class PedidoService {

    private static final Logger log = LoggerFactory.getLogger(PedidoService.class);

    private final PedidoRepository pedidoRepository;
    private final PedidoItemRepository pedidoItemRepository;
    private final PedidoSnsService pedidoSnsService;

    public PedidoService(
            PedidoRepository pedidoRepository,
            PedidoItemRepository pedidoItemRepository,
            PedidoSnsService pedidoSnsService) {

        this.pedidoRepository = pedidoRepository;
        this.pedidoItemRepository = pedidoItemRepository;
        this.pedidoSnsService = pedidoSnsService;
    }

    @Transactional
    public PedidoEntity crearPedido(
            String cliente,
            List<PedidoItemEntity> items,
            BigDecimal total) {

        // Validar los datos recibidos.
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

        // Crear y guardar el pedido principal.
        PedidoEntity pedido = new PedidoEntity();
        pedido.setCliente(cliente.trim());
        pedido.setTotal(total);
        pedido.setEstado("RECIBIDO");

        PedidoEntity guardado = pedidoRepository.saveAndFlush(pedido);

        // Asociar cada producto al pedido.
        for (PedidoItemEntity item : items) {
            if (item == null) {
                throw new IllegalArgumentException(
                        "El pedido contiene un producto inválido");
            }

            item.setPedido(guardado);
        }

        // Guardar los detalles del pedido.
        pedidoItemRepository.saveAll(items);
        pedidoItemRepository.flush();

        // Publicar en SNS únicamente después de confirmar
        // la transacción de base de datos.
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {

                    @Override
                    public void afterCommit() {
                        try {
                            pedidoSnsService.publicarPedido(guardado);

                            log.info(
                                    "Evento SNS publicado para el pedido {}",
                                    guardado.getId());

                        } catch (Exception e) {
                            log.error(
                                    "El pedido {} se guardó, pero falló la publicación en SNS",
                                    guardado.getId(),
                                    e);
                        }
                    }
                });

        return guardado;
    }
}
