package com.bodegapeirano.pedidos.controller;

import com.bodegapeirano.pedidos.model.Pedido;
import com.bodegapeirano.pedidos.model.PedidoRequest;
import com.bodegapeirano.pedidos.service.PedidosService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/pedidos")
public class PedidosController {

    private final PedidosService pedidosService;

    public PedidosController(PedidosService pedidosService) {
        this.pedidosService = pedidosService;
    }

    // Endpoint 1: Crear nuevo pedido (con llamada HTTP de validación a Servicio A)
    @PostMapping
    public ResponseEntity<Pedido> crearPedido(@RequestBody PedidoRequest request) {
        Pedido resultado = pedidosService.crearPedido(request);
        if ("APROBADO".equals(resultado.getEstado())) {
            return ResponseEntity.status(HttpStatus.CREATED).body(resultado);
        } else if ("RECHAZADO".equals(resultado.getEstado())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(resultado);
        } else {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(resultado);
        }
    }

    // Endpoint 2: Listar todos los pedidos (filtro opcional por estado: APROBADO, RECHAZADO, FALLIDO)
    @GetMapping
    public ResponseEntity<List<Pedido>> listarPedidos(@RequestParam(required = false) String estado) {
        return ResponseEntity.ok(pedidosService.listarPedidos(estado));
    }

    // Endpoint 3: Obtener pedido por ID
    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerPorId(@PathVariable String id) {
        return pedidosService.buscarPorId(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "error", "Pedido no encontrado",
                        "id", id
                )));
    }

    // Endpoint 4: Buscar pedidos por nombre de cliente
    @GetMapping("/cliente/{cliente}")
    public ResponseEntity<List<Pedido>> buscarPorCliente(@PathVariable String cliente) {
        return ResponseEntity.ok(pedidosService.buscarPorCliente(cliente));
    }

    // Endpoint 5: Resumen y métricas de negocio de ventas
    @GetMapping("/resumen")
    public ResponseEntity<Map<String, Object>> obtenerResumen() {
        return ResponseEntity.ok(pedidosService.obtenerResumen());
    }

    // Endpoint 6: Alternar simulación de fallas para pruebas de caos y monitoreo
    @PostMapping("/simular-falla")
    public ResponseEntity<Map<String, Object>> simularFalla(@RequestParam(defaultValue = "true") boolean activa) {
        boolean estadoActual = pedidosService.alternarSimulacionFalla(activa);
        return ResponseEntity.ok(Map.of(
                "mensaje", "Simulacion de falla en pedidos actualizada",
                "simulacionActiva", estadoActual
        ));
    }
}
