package com.coffeeflower.bff.service;

import com.coffeeflower.bff.model.PedidoEntity;
import com.coffeeflower.bff.model.PedidoItemEntity;s
import com.coffeeflower.bff.repository.PedidoRepository;
import com.coffeeflower.bff.repository.PedidoItemRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.List;

@Service
public class PedidoService {

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

        if (cliente == null || cliente.isBlank()) {
            throw new IllegalArgumentException(
                    "El nombre del cliente es obligatorio");
        }

        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException(
                    "El pedido debe contener productos");
        }

        if (total == null || total.signum() < 0) {
            throw new IllegalArgumentException(
                    "El total del pedido no es válido");
        }

        PedidoEntity pedido = new PedidoEntity();
        pedido.setCliente(cliente);
        pedido.setTotal(total);
        pedido.setEstado("RECIBIDO");

        // Guardar el pedido principal.
        PedidoEntity guardado = pedidoRepository.saveAndFlush(pedido);

        // Asociar los productos al pedido.
        for (PedidoItemEntity item : items) {
            item.setPedido(guardado);
        }

        // Guardar los detalles.
        pedidoItemRepository.saveAll(items);

        // Publicar el evento SNS después de confirmar la transacción.
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        pedidoSnsService.publicarPedido(guardado);
                    }
                });

        return guardado;
    }
}
