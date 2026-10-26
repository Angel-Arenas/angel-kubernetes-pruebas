package com.bodegapeirano.catalogo.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

@Component
public class CatalogoMetrics {

    private final MeterRegistry registry;
    private final Counter consultasExitosasCounter;
    private final Counter consultasFallidasCounter;
    private final Counter stockDescontadoTotalCounter;
    private final Timer consultaTimer;

    public CatalogoMetrics(MeterRegistry registry) {
        this.registry = registry;

        // 1. Counter: Consultas al catálogo (desglosado por resultado)
        this.consultasExitosasCounter = Counter.builder("bodega_catalogo_consultas_total")
                .description("Total de consultas realizadas al catalogo de vinos")
                .tag("resultado", "ok")
                .register(registry);

        this.consultasFallidasCounter = Counter.builder("bodega_catalogo_consultas_total")
                .description("Total de consultas realizadas al catalogo de vinos")
                .tag("resultado", "no_encontrado")
                .register(registry);

        // 2. Counter: Unidades totales de vino descontadas del stock
        this.stockDescontadoTotalCounter = Counter.builder("bodega_catalogo_stock_descontado_total")
                .description("Total de unidades de vino descontadas del inventario por ventas")
                .register(registry);

        // 3. Timer: Duración de consultas al catálogo
        this.consultaTimer = Timer.builder("bodega_catalogo_consulta_duracion_seconds")
                .description("Tiempo empleado en procesar consultas al catalogo")
                .publishPercentileHistogram()
                .register(registry);

        // 4. Gauge: Delay artificial configurado en segundos (Casuística de Servicio Lento)
        Gauge.builder("bodega_catalogo_artificial_delay_seconds", this.artificialDelaySeconds, java.util.concurrent.atomic.AtomicInteger::get)
                .description("Delay artificial inducido en segundos para casuistica de degradacion de rendimiento")
                .register(registry);
    }

    private final java.util.concurrent.atomic.AtomicInteger artificialDelaySeconds = new java.util.concurrent.atomic.AtomicInteger(0);

    /**
     * 5. Gauge: Stock total actual en bodega
     */
    public void registrarGaugeStockTotal(Supplier<Number> stockTotalSupplier) {
        Gauge.builder("bodega_catalogo_stock_total", stockTotalSupplier)
                .description("Cantidad total de botellas en inventario en Bodega Peirano")
                .register(registry);
    }

    /**
     * 6. Gauge: Variedades de vino activas
     */
    public void registrarGaugeVinosActivos(Supplier<Number> variedadesSupplier) {
        Gauge.builder("bodega_catalogo_vinos_activos", variedadesSupplier)
                .description("Numero total de variedades o etiquetas de vino activas")
                .register(registry);
    }

    public void setArtificialDelaySeconds(int seconds) {
        this.artificialDelaySeconds.set(seconds);
    }

    public int getArtificialDelaySeconds() {
        return this.artificialDelaySeconds.get();
    }

    public void incrementarConsultasExitosas() {
        consultasExitosasCounter.increment();
    }

    public void incrementarConsultasFallidas() {
        consultasFallidasCounter.increment();
    }

    public void registrarStockDescontado(int cantidad) {
        stockDescontadoTotalCounter.increment(cantidad);
    }

    public <T> T medirTiempoConsulta(Callable<T> callable) throws Exception {
        return consultaTimer.recordCallable(callable);
    }

    public void medirTiempo(Runnable runnable) {
        consultaTimer.record(runnable);
    }

    public MeterRegistry getRegistry() {
        return registry;
    }
}
