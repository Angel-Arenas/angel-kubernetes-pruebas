package com.bodegapeirano.pedidos.controller;

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MetricsRedirectController {

    private final ObjectProvider<PrometheusMeterRegistry> prometheusMeterRegistryProvider;

    public MetricsRedirectController(ObjectProvider<PrometheusMeterRegistry> prometheusMeterRegistryProvider) {
        this.prometheusMeterRegistryProvider = prometheusMeterRegistryProvider;
    }

    /**
     * Endpoint directo en /metrics requerido expresamente por la rúbrica del proyecto,
     * devolviendo el formato de scrape oficial de Prometheus.
     */
    @GetMapping(value = "/metrics", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> metrics() {
        PrometheusMeterRegistry registry = prometheusMeterRegistryProvider.getIfAvailable();
        if (registry != null) {
            return ResponseEntity.ok(registry.scrape());
        }
        return ResponseEntity.ok("# No PrometheusMeterRegistry available\n");
    }
}
