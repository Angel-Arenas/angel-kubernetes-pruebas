package com.bodegapeirano.catalogo.service;

import com.bodegapeirano.catalogo.metrics.CatalogoMetrics;
import com.bodegapeirano.catalogo.model.Vino;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class VinosService {

    private final Map<String, Vino> inventario = new ConcurrentHashMap<>();
    private final CatalogoMetrics metrics;

    @org.springframework.beans.factory.annotation.Value("${ARTIFICIAL_DELAY_SECONDS:${catalogo.delay-seconds:0}}")
    private int initialDelaySeconds;

    public VinosService(CatalogoMetrics metrics) {
        this.metrics = metrics;
    }

    @PostConstruct
    public void init() {
        // Carga inicial en memoria de Bodega Peirano
        guardarVino(new Vino("VINO-001", "Gran Reserva Malbec", "Bodega Peirano", "Tinto", "Malbec", 2019, 85.00, 40, "Crianza de 18 meses en barricas de roble frances"));
        guardarVino(new Vino("VINO-002", "Cabernet Sauvignon Roble", "Bodega Peirano", "Tinto", "Cabernet Sauvignon", 2021, 72.00, 35, "Notas a frutos negros, pimienta y vainilla"));
        guardarVino(new Vino("VINO-003", "Chardonnay Reserva de Altura", "Bodega Peirano", "Blanco", "Chardonnay", 2022, 68.00, 30, "Fresco, notas citricas y final mantecoso elegante"));
        guardarVino(new Vino("VINO-004", "Torrontes Valle Fertil", "Bodega Peirano", "Blanco", "Torrontes", 2023, 54.00, 25, "Aromas florales intensos a jazmin y durazno blanco"));
        guardarVino(new Vino("VINO-005", "Tannat Corte Especial", "Bodega Peirano", "Tinto", "Tannat", 2020, 95.00, 20, "Vino de gran estructura, taninos firmes y persistentes"));
        guardarVino(new Vino("VINO-006", "Rosado Malbec Peirano", "Bodega Peirano", "Rosado", "Malbec", 2023, 48.00, 15, "Delicado color salmon con aromas a frambuesas y cerezas"));

        // Inicializar Gauge de delay artificial con el valor inyectado por variable de entorno
        metrics.setArtificialDelaySeconds(initialDelaySeconds);

        // Registrar Gauges en Micrometer
        metrics.registrarGaugeStockTotal(this::calcularStockTotal);
        metrics.registrarGaugeVinosActivos(this::contarVinos);
    }

    private void guardarVino(Vino v) {
        inventario.put(v.getId(), v);
    }

    private void aplicarDelaySiConfigurado() {
        int delay = metrics.getArtificialDelaySeconds();
        if (delay > 0) {
            try {
                TimeUnit.SECONDS.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public List<Vino> listarTodos(String categoria, String varietal, Boolean soloConStock) {
        aplicarDelaySiConfigurado();
        return metrics.getRegistry().timer("bodega_catalogo_consulta_duracion_seconds").record(() -> {
            metrics.incrementarConsultasExitosas();
            return inventario.values().stream()
                    .filter(v -> categoria == null || v.getCategoria().equalsIgnoreCase(categoria))
                    .filter(v -> varietal == null || v.getVarietal().equalsIgnoreCase(varietal))
                    .filter(v -> soloConStock == null || (soloConStock ? v.getStock() > 0 : true))
                    .sorted(Comparator.comparing(Vino::getId))
                    .collect(Collectors.toList());
        });
    }

    public Optional<Vino> buscarPorId(String id) {
        aplicarDelaySiConfigurado();
        return metrics.getRegistry().timer("bodega_catalogo_consulta_duracion_seconds").record(() -> {
            Vino vino = inventario.get(id);
            if (vino != null) {
                metrics.incrementarConsultasExitosas();
                return Optional.of(vino);
            } else {
                metrics.incrementarConsultasFallidas();
                return Optional.empty();
            }
        });
    }

    public synchronized Vino registrarVino(Vino nuevo) {
        if (nuevo.getId() == null || nuevo.getId().isBlank()) {
            nuevo.setId("VINO-" + String.format("%03d", inventario.size() + 1));
        }
        if (nuevo.getBodega() == null || nuevo.getBodega().isBlank()) {
            nuevo.setBodega("Bodega Peirano");
        }
        inventario.put(nuevo.getId(), nuevo);
        return nuevo;
    }

    public synchronized boolean descontarStock(String id, int cantidad) {
        aplicarDelaySiConfigurado();
        Vino vino = inventario.get(id);
        if (vino == null) {
            metrics.incrementarConsultasFallidas();
            return false;
        }
        if (vino.getStock() < cantidad) {
            return false;
        }
        vino.setStock(vino.getStock() - cantidad);
        metrics.registrarStockDescontado(cantidad);
        return true;
    }

    public synchronized boolean restaurarStock(String id, int cantidad) {
        Vino vino = inventario.get(id);
        if (vino == null) {
            return false;
        }
        vino.setStock(vino.getStock() + cantidad);
        return true;
    }

    public int calcularStockTotal() {
        return inventario.values().stream().mapToInt(Vino::getStock).sum();
    }

    public int contarVinos() {
        return inventario.size();
    }

    public void simularDemora(int segundos) {
        metrics.setArtificialDelaySeconds(segundos);
        try {
            TimeUnit.SECONDS.sleep(segundos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void setArtificialDelay(int segundos) {
        metrics.setArtificialDelaySeconds(segundos);
    }

    public CatalogoMetrics getMetrics() {
        return metrics;
    }
}
