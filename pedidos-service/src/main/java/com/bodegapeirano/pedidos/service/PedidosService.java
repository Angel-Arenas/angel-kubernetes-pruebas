package com.bodegapeirano.pedidos.service;

import com.bodegapeirano.pedidos.client.CatalogoClient;
import com.bodegapeirano.pedidos.metrics.PedidosMetrics;
import com.bodegapeirano.pedidos.model.Pedido;
import com.bodegapeirano.pedidos.model.PedidoRequest;
import com.bodegapeirano.pedidos.model.VinoDto;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
public class PedidosService {

    private static final Logger log = LoggerFactory.getLogger(PedidosService.class);

    private final Map<String, Pedido> pedidos = new ConcurrentHashMap<>();
    private final AtomicLong contadorPedidos = new AtomicLong(1);
    private final AtomicBoolean simulacionFallaActiva = new AtomicBoolean(false);

    private final CatalogoClient catalogoClient;
    private final PedidosMetrics metrics;

    public PedidosService(CatalogoClient catalogoClient, PedidosMetrics metrics) {
        this.catalogoClient = catalogoClient;
        this.metrics = metrics;
    }

    @PostConstruct
    public void init() {
        // Registrar Gauges dinámicos
        metrics.registrarGaugePedidosTotales(this::contarTotalPedidos);
        metrics.registrarGaugeTasaRechazo(this::calcularTasaRechazo);
    }

    /**
     * Procesa la creación de un pedido validando en tiempo real con el Servicio A (Catálogo).
     */
    public Pedido crearPedido(PedidoRequest request) {
        String idGenerado = "PED-" + String.format("%04d", contadorPedidos.getAndIncrement());

        return metrics.getRegistry().timer("bodega_pedidos_procesamiento_duracion_seconds").record(() -> {
            // Validación de casuística de prueba de falla forzada
            if (simulacionFallaActiva.get()) {
                Pedido pedidoFallido = new Pedido(
                        idGenerado,
                        request.getCliente() != null ? request.getCliente() : "Cliente Anonimo",
                        request.getVinoId(),
                        "Desconocido",
                        request.getCantidad(),
                        0.0,
                        0.0,
                        "FALLIDO",
                        "Simulacion de falla critica en pedidos activada"
                );
                pedidos.put(idGenerado, pedidoFallido);
                metrics.registrarPedido("fallido", "simulacion_error");
                return pedidoFallido;
            }

            // Validaciones básicas de entrada
            if (request.getCantidad() <= 0) {
                Pedido pedidoRechazado = new Pedido(
                        idGenerado,
                        request.getCliente() != null ? request.getCliente() : "Cliente Anonimo",
                        request.getVinoId(),
                        "No asignado",
                        request.getCantidad(),
                        0.0,
                        0.0,
                        "RECHAZADO",
                        "La cantidad solicitada debe ser mayor a 0"
                );
                pedidos.put(idGenerado, pedidoRechazado);
                metrics.registrarPedido("rechazado", "cantidad_invalida");
                return pedidoRechazado;
            }

            try {
                // 1. Comunicación HTTP con Servicio A para consultar existencia y stock
                Optional<VinoDto> optVino = catalogoClient.consultarVino(request.getVinoId());

                if (optVino.isEmpty()) {
                    Pedido pedidoRechazado = new Pedido(
                            idGenerado,
                            request.getCliente(),
                            request.getVinoId(),
                            "Vino Desconocido",
                            request.getCantidad(),
                            0.0,
                            0.0,
                            "RECHAZADO",
                            "Vino no encontrado en catálogo"
                    );
                    pedidos.put(idGenerado, pedidoRechazado);
                    metrics.registrarPedido("rechazado", "vino_no_encontrado");
                    return pedidoRechazado;
                }

                VinoDto vino = optVino.get();

                // 2. Validación de disponibilidad de stock
                if (vino.getStock() < request.getCantidad()) {
                    Pedido pedidoRechazado = new Pedido(
                            idGenerado,
                            request.getCliente(),
                            vino.getId(),
                            vino.getNombre(),
                            request.getCantidad(),
                            vino.getPrecio(),
                            0.0,
                            "RECHAZADO",
                            String.format("Stock insuficiente (Disponible: %d, Solicitado: %d)", vino.getStock(), request.getCantidad())
                    );
                    pedidos.put(idGenerado, pedidoRechazado);
                    metrics.registrarPedido("rechazado", "stock_insuficiente");
                    return pedidoRechazado;
                }

                // 3. Solicitud de descuento de stock en Servicio A
                boolean stockDescontado = catalogoClient.descontarStock(vino.getId(), request.getCantidad());

                if (stockDescontado) {
                    double total = Math.round(request.getCantidad() * vino.getPrecio() * 100.0) / 100.0;
                    Pedido pedidoAprobado = new Pedido(
                            idGenerado,
                            request.getCliente(),
                            vino.getId(),
                            vino.getNombre(),
                            request.getCantidad(),
                            vino.getPrecio(),
                            total,
                            "APROBADO",
                            "Pedido aprobado y stock descontado exitosamente en bodega"
                    );
                    pedidos.put(idGenerado, pedidoAprobado);
                    metrics.registrarPedido("aprobado", "ok");
                    metrics.registrarIngreso(total);
                    return pedidoAprobado;
                } else {
                    Pedido pedidoRechazado = new Pedido(
                            idGenerado,
                            request.getCliente(),
                            vino.getId(),
                            vino.getNombre(),
                            request.getCantidad(),
                            vino.getPrecio(),
                            0.0,
                            "RECHAZADO",
                            "No se pudo reservar el stock en catalogo (posible concurrencia)"
                    );
                    pedidos.put(idGenerado, pedidoRechazado);
                    metrics.registrarPedido("rechazado", "falla_reserva_stock");
                    return pedidoRechazado;
                }

            } catch (Exception ex) {
                log.error("Excepción comunicando con Servicio A: {}", ex.getMessage());
                Pedido pedidoFallido = new Pedido(
                        idGenerado,
                        request.getCliente(),
                        request.getVinoId(),
                        "No disponible",
                        request.getCantidad(),
                        0.0,
                        0.0,
                        "FALLIDO",
                        "Servicio de catalogo no responde o esta caido: " + ex.getMessage()
                );
                pedidos.put(idGenerado, pedidoFallido);
                metrics.registrarPedido("fallido", "servicio_catalogo_down");
                return pedidoFallido;
            }
        });
    }

    public List<Pedido> listarPedidos(String estado) {
        return pedidos.values().stream()
                .filter(p -> estado == null || p.getEstado().equalsIgnoreCase(estado))
                .sorted(Comparator.comparing(Pedido::getFechaHora).reversed())
                .collect(Collectors.toList());
    }

    public Optional<Pedido> buscarPorId(String id) {
        return Optional.ofNullable(pedidos.get(id));
    }

    public List<Pedido> buscarPorCliente(String cliente) {
        return pedidos.values().stream()
                .filter(p -> p.getCliente() != null && p.getCliente().toLowerCase().contains(cliente.toLowerCase()))
                .sorted(Comparator.comparing(Pedido::getFechaHora).reversed())
                .collect(Collectors.toList());
    }

    public Map<String, Object> obtenerResumen() {
        long total = pedidos.size();
        long aprobados = pedidos.values().stream().filter(p -> "APROBADO".equalsIgnoreCase(p.getEstado())).count();
        long rechazados = pedidos.values().stream().filter(p -> "RECHAZADO".equalsIgnoreCase(p.getEstado())).count();
        long fallidos = pedidos.values().stream().filter(p -> "FALLIDO".equalsIgnoreCase(p.getEstado())).count();
        double ingresos = pedidos.values().stream()
                .filter(p -> "APROBADO".equalsIgnoreCase(p.getEstado()))
                .mapToDouble(Pedido::getMontoTotal)
                .sum();
        double tasaRechazo = total > 0 ? ((double) (rechazados + fallidos) * 100.0 / total) : 0.0;

        Map<String, Object> resumen = new HashMap<>();
        resumen.put("totalPedidos", total);
        resumen.put("aprobados", aprobados);
        resumen.put("rechazados", rechazados);
        resumen.put("fallidos", fallidos);
        resumen.put("ingresosTotalesPen", Math.round(ingresos * 100.0) / 100.0);
        resumen.put("tasaRechazoPorcentaje", Math.round(tasaRechazo * 10.0) / 10.0);
        resumen.put("simulacionFallaActiva", simulacionFallaActiva.get());
        return resumen;
    }

    public boolean alternarSimulacionFalla(boolean activa) {
        simulacionFallaActiva.set(activa);
        return simulacionFallaActiva.get();
    }

    public int contarTotalPedidos() {
        return pedidos.size();
    }

    public double calcularTasaRechazo() {
        long total = pedidos.size();
        if (total == 0) return 0.0;
        long noAprobados = pedidos.values().stream()
                .filter(p -> !"APROBADO".equalsIgnoreCase(p.getEstado()))
                .count();
        return (double) noAprobados * 100.0 / (double) total;
    }
}
