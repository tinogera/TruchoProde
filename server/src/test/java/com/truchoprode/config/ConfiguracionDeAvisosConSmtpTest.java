package com.truchoprode.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.truchoprode.service.EnviadorDeAvisos;
import com.truchoprode.service.EnviadorPorMail;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "truchoprode.avisos.transporte=mail")
@ActiveProfiles("test")
@DisplayName("Configuracion de avisos, con SMTP")
class ConfiguracionDeAvisosConSmtpTest {

    @Autowired private EnviadorDeAvisos enviador;

    @Test
    @DisplayName("con transporte=mail se usa el enviador por SMTP")
    void conTransporteMailUsaSmtp() {
        assertThat(enviador).isInstanceOf(EnviadorPorMail.class);
    }
}
