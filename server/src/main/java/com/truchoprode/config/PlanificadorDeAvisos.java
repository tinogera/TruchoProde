package com.truchoprode.config;

import com.truchoprode.service.AvisoDePrediccionService;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Revisa cada pocos minutos si hay avisos para mandar. Es solo el disparador: toda la logica esta
 * en el service, asi se puede probar sin esperar al reloj.
 */
@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "truchoprode.avisos",
        name = "habilitados",
        havingValue = "true",
        matchIfMissing = true)
public class PlanificadorDeAvisos {

    private final AvisoDePrediccionService service;

    public PlanificadorDeAvisos(AvisoDePrediccionService service) {
        this.service = service;
    }

    @Scheduled(
            fixedDelayString = "${truchoprode.avisos.cada-minutos}",
            timeUnit = TimeUnit.MINUTES)
    public void avisarPendientes() {
        int enviados = service.avisarPendientes(Instant.now());
        if (enviados > 0) {
            log.info("Avisos de prediccion enviados: {}", enviados);
        }
    }
}
