package com.truchoprode.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.truchoprode.domain.MensajeDeAviso;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
@DisplayName("Enviador por mail")
class EnviadorPorMailTest {

    @Mock private JavaMailSender javaMailSender;

    @Captor private ArgumentCaptor<SimpleMailMessage> mailCapturado;

    @Test
    @DisplayName("arma el mail con el remitente configurado y los datos del mensaje")
    void armaElMailConElRemitenteConfigurado() {
        EnviadorPorMail enviador =
                new EnviadorPorMail(javaMailSender, "TruchoProde <no-reply@truchoprode.local>");

        enviador.enviar(new MensajeDeAviso("santino@ejemplo.com", "Te falta 1", "Hola santino"));

        verify(javaMailSender).send(mailCapturado.capture());
        SimpleMailMessage mail = mailCapturado.getValue();
        assertThat(mail.getFrom()).isEqualTo("TruchoProde <no-reply@truchoprode.local>");
        assertThat(mail.getTo()).containsExactly("santino@ejemplo.com");
        assertThat(mail.getSubject()).isEqualTo("Te falta 1");
        assertThat(mail.getText()).isEqualTo("Hola santino");
    }
}
