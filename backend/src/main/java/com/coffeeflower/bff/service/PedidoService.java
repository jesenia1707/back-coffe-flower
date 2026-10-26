package com.coffeeflower.bff.service;

import com.coffeeflower.bff.model.PedidoEntity;
import com.coffeeflower.bff.model.PedidoItemEntity;
import com.coffeeflower.bff.repository.PedidoRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final PedidoSnsService pedidoSnsService;

    public PedidoService(
            PedidoRepository pedidoRepository,
            PedidoSnsService pedidoSnsService) {
        this.pedidoRepository = pedidoRepository;
        this.pedidoSnsService = pedidoSnsService;
    }

    @Transactional
    public PedidoEntity crearPedido(
            String cliente,
            List<PedidoItemEntity> items,
            java.math.BigDecimal total) {

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

        PedidoEntity guardado = pedidoRepository.save(pedido);

        for (PedidoItemEntity item : items) {
            item.setPedido(guardado);
        }

        // Próximo paso: guardar los detalles del pedido.
        // También configuraremos el envío a SNS después de confirmar
        // que el pedido y sus detalles se guardan correctamente.

        return guardado;
    }
}