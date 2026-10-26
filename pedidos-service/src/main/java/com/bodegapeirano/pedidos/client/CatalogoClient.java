package com.bodegapeirano.pedidos.client;

import com.bodegapeirano.pedidos.model.VinoDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Optional;

@Component
public class CatalogoClient {

    private static final Logger log = LoggerFactory.getLogger(CatalogoClient.class);
    private final WebClient webClient;

    public CatalogoClient(WebClient.Builder webClientBuilder,
                          @Value("${catalogo.service.url:http://localhost:8081}") String catalogoUrl) {
        log.info("Inicializando CatalogoClient conectado a: {}", catalogoUrl);
        this.webClient = webClientBuilder
                .baseUrl(catalogoUrl)
                .build();
    }

    /**
     * Consulta al Servicio A si el vino existe y obtiene su información de stock y precio.
     */
    public Optional<VinoDto> consultarVino(String vinoId) {
        try {
            return webClient.get()
                    .uri("/api/vinos/{id}", vinoId)
                    .retrieve()
                    .bodyToMono(VinoDto.class)
                    .timeout(Duration.ofSeconds(4))
                    .map(Optional::of)
                    .onErrorResume(WebClientResponseException.NotFound.class, e -> {
                        log.warn("Vino no encontrado en catálogo (HTTP 404): {}", vinoId);
                        return Mono.just(Optional.empty());
                    })
                    .onErrorResume(e -> {
                        log.error("Error al comunicar con Servicio de Catálogo para vinoId {}: {}", vinoId, e.getMessage());
                        return Mono.error(new RuntimeException("Servicio de catalogo no disponible: " + e.getMessage()));
                    })
                    .block();
        } catch (Exception ex) {
            log.error("Falla en llamada a Servicio A: {}", ex.getMessage());
            throw ex;
        }
    }

    /**
     * Solicita al Servicio A descontar el stock una vez validado el pedido.
     */
    public boolean descontarStock(String vinoId, int cantidad) {
        try {
            return Boolean.TRUE.equals(webClient.put()
                    .uri(uriBuilder -> uriBuilder
                            .path("/api/vinos/{id}/descontar-stock")
                            .queryParam("cantidad", cantidad)
                            .build(vinoId))
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(Duration.ofSeconds(4))
                    .map(resp -> resp.getStatusCode().is2xxSuccessful())
                    .onErrorResume(e -> {
                        log.warn("Fallo al descontar stock en Servicio A para vino {}: {}", vinoId, e.getMessage());
                        return Mono.just(false);
                    })
                    .block());
        } catch (Exception e) {
            log.error("Error al descontar stock: {}", e.getMessage());
            return false;
        }
    }
}
