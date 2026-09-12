package com.truchoprode.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.truchoprode.service.AvisoDePrediccionService;
import com.truchoprode.service.EnviadorDeAvisos;
import com.truchoprode.service.EnviadorPorLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Configuracion de avisos, por defecto")
class ConfiguracionDeAvisosTest {

    @Autowired private EnviadorDeAvisos enviador;

    @Autowired private AvisoDePrediccionService service;

    @Test
    @DisplayName("sin configurar nada escribe en el log en vez de mandar mails de verdad")
    void porDefectoNoMandaMailsDeVerdad() {
        assertThat(enviador).isInstanceOf(EnviadorPorLog.class);
    }

    @Test
    @DisplayName("el service queda armado y usable")
    void elServiceQuedaArmado() {
        assertThat(service).isNotNull();
    }
}
