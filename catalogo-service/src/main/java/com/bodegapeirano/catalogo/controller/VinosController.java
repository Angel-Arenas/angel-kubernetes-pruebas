package com.bodegapeirano.catalogo.controller;

import com.bodegapeirano.catalogo.model.Vino;
import com.bodegapeirano.catalogo.service.VinosService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/vinos")
public class VinosController {

    private final VinosService service;

    public VinosController(VinosService service) {
        this.service = service;
    }

    // Endpoint 1: Listar todos los vinos del catálogo (con filtros opcionales)
    @GetMapping
    public ResponseEntity<List<Vino>> listarVinos(
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) String varietal,
            @RequestParam(required = false) Boolean soloConStock) {
        return ResponseEntity.ok(service.listarTodos(categoria, varietal, soloConStock));
    }

    // Endpoint 2: Obtener vino por ID
    @GetMapping("/{id}")
    public ResponseEntity<?> obtenerPorId(@PathVariable String id) {
        return service.buscarPorId(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                        "error", "Vino no encontrado",
                        "id", id
                )));
    }

    // Endpoint 3: Registrar nuevo vino en catálogo
    @PostMapping
    public ResponseEntity<Vino> crearVino(@RequestBody Vino nuevoVino) {
        Vino creado = service.registrarVino(nuevoVino);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    // Endpoint 4: Descontar stock (utilizado por el Servicio B de Pedidos)
    @PutMapping("/{id}/descontar-stock")
    public ResponseEntity<?> descontarStock(
            @PathVariable String id,
            @RequestParam(defaultValue = "1") int cantidad) {
        if (cantidad <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "La cantidad debe ser mayor a 0"));
        }

        boolean exito = service.descontarStock(id, cantidad);
        if (exito) {
            return ResponseEntity.ok(Map.of(
                    "mensaje", "Stock descontado exitosamente",
                    "vinoId", id,
                    "cantidadDescontada", cantidad
            ));
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "error", "No fue posible descontar stock (vino inexistente o stock insuficiente)",
                    "vinoId", id,
                    "cantidadSolicitada", cantidad
            ));
        }
    }

    // Endpoint 5: Restaurar stock (cancelaciones)
    @PutMapping("/{id}/restaurar-stock")
    public ResponseEntity<?> restaurarStock(
            @PathVariable String id,
            @RequestParam(defaultValue = "1") int cantidad) {
        boolean exito = service.restaurarStock(id, cantidad);
        if (exito) {
            return ResponseEntity.ok(Map.of(
                    "mensaje", "Stock restaurado exitosamente",
                    "vinoId", id,
                    "cantidadRestaurada", cantidad
            ));
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Vino no encontrado"));
    }

    // Endpoint 6: Obtener resumen de stock total de la bodega
    @GetMapping("/stock-total")
    public ResponseEntity<Map<String, Object>> obtenerStockTotal() {
        Map<String, Object> resp = new HashMap<>();
        resp.put("bodega", "Bodega Peirano");
        resp.put("totalBotellas", service.calcularStockTotal());
        resp.put("totalVariedades", service.contarVinos());
        return ResponseEntity.ok(resp);
    }

    // Endpoint 7: Simulación de lentitud para casuística de monitoreo
    @GetMapping("/simular-demora")
    public ResponseEntity<Map<String, Object>> simularDemora(
            @RequestParam(defaultValue = "3") int segundos) {
        long inicio = System.currentTimeMillis();
        service.simularDemora(segundos);
        long duracion = System.currentTimeMillis() - inicio;

        return ResponseEntity.ok(Map.of(
                "mensaje", "Demora simulada completada exitosamente",
                "segundosSimulados", segundos,
                "duracionRealMs", duracion
        ));
    }
}
