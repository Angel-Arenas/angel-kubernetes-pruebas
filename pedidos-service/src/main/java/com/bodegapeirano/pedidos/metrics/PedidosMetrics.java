package com.bodegapeirano.pedidos.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

@Component
public class PedidosMetrics {

    private final MeterRegistry registry;
    private final Counter ingresosCounter;
    private final Timer procesamientoTimer;

    public PedidosMetrics(MeterRegistry registry) {
        this.registry = registry;

        // 1. Counter: Ingresos acumulados en soles por ventas de vino
        this.ingresosCounter = Counter.builder("bodega_pedidos_ingresos_total")
                .description("Monto total de ventas e ingresos acumulados en Soles (PEN)")
                .baseUnit("soles")
                .register(registry);

        // 2. Timer: Duración del procesamiento completo de pedidos (incluyendo llamada HTTP a Servicio A)
        this.procesamientoTimer = Timer.builder("bodega_pedidos_procesamiento_duracion_seconds")
                .description("Tiempo empleado en validar con Servicio A y procesar el pedido")
                .publishPercentileHistogram()
                .register(registry);
    }

    /**
     * 3. Counter dinámico por estado ("aprobado", "rechazado", "fallido") y motivo
     */
    public void registrarPedido(String estado, String motivo) {
        Counter.builder("bodega_pedidos_creados_total")
                .description("Total de pedidos procesados por el servicio")
                .tag("estado", estado.toLowerCase())
                .tag("motivo", motivo != null ? motivo.toLowerCase().replace(" ", "_") : "general")
                .register(registry)
                .increment();
    }

    public void registrarIngreso(double monto) {
        if (monto > 0) {
            ingresosCounter.increment(monto);
        }
    }

    /**
     * 4. Gauge: Total de pedidos en memoria
     */
    public void registrarGaugePedidosTotales(Supplier<Number> pedidosSupplier) {
        Gauge.builder("bodega_pedidos_activos_total", pedidosSupplier)
                .description("Numero total de pedidos almacenados en el sistema")
                .register(registry);
    }

    /**
     * 5. Gauge: Tasa de rechazo de pedidos (%)
     */
    public void registrarGaugeTasaRechazo(Supplier<Number> tasaRechazoSupplier) {
        Gauge.builder("bodega_pedidos_tasa_rechazo", tasaRechazoSupplier)
                .description("Porcentaje de pedidos rechazados respecto al total de pedidos")
                .baseUnit("percent")
                .register(registry);
    }

    public <T> T medirTiempoProcesamiento(Callable<T> callable) throws Exception {
        return procesamientoTimer.recordCallable(callable);
    }

    public MeterRegistry getRegistry() {
        return registry;
    }
}
