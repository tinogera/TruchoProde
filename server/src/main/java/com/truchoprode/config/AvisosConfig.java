package com.truchoprode.config;

import com.truchoprode.mapper.AvisoMapper;
import com.truchoprode.service.AvisoDePrediccionService;
import com.truchoprode.service.EnviadorDeAvisos;
import com.truchoprode.service.EnviadorPorLog;
import com.truchoprode.service.EnviadorPorMail;
import com.truchoprode.service.RedactorDeAvisos;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Arma las piezas del aviso de prediccion. El transporte se elige por configuracion y el default
 * es el de log, para que nunca salga un mail de verdad sin haberlo pedido explicitamente.
 */
@Configuration
@EnableScheduling
public class AvisosConfig {

    @Bean
    public RedactorDeAvisos redactorDeAvisos(
            @Value("${truchoprode.zona-horaria}") String zonaHoraria,
            @Value("${truchoprode.avisos.url-base}") String urlBase) {
        return new RedactorDeAvisos(ZoneId.of(zonaHoraria), urlBase);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "truchoprode.avisos",
            name = "transporte",
            havingValue = "log",
            matchIfMissing = true)
    public EnviadorDeAvisos enviadorPorLog() {
        return new EnviadorPorLog();
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "truchoprode.avisos",
            name = "transporte",
            havingValue = "mail")
    public EnviadorDeAvisos enviadorPorMail(
            JavaMailSender javaMailSender,
            @Value("${truchoprode.avisos.remitente}") String remitente) {
        return new EnviadorPorMail(javaMailSender, remitente);
    }

    @Bean
    public AvisoDePrediccionService avisoDePrediccionService(
            AvisoMapper mapper,
            EnviadorDeAvisos enviador,
            RedactorDeAvisos redactor,
            @Value("${truchoprode.avisos.anticipacion-minutos}") int anticipacionMinutos) {
        return new AvisoDePrediccionService(mapper, enviador, redactor, anticipacionMinutos);
    }
}
