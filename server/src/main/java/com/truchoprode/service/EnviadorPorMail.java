package com.truchoprode.service;

import com.truchoprode.domain.MensajeDeAviso;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/** Manda el aviso por SMTP. Se usa cuando truchoprode.avisos.transporte = mail. */
public class EnviadorPorMail implements EnviadorDeAvisos {

    private final JavaMailSender javaMailSender;
    private final String remitente;

    public EnviadorPorMail(JavaMailSender javaMailSender, String remitente) {
        this.javaMailSender = javaMailSender;
        this.remitente = remitente;
    }

    @Override
    public void enviar(MensajeDeAviso mensaje) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(remitente);
        mail.setTo(mensaje.destinatario());
        mail.setSubject(mensaje.asunto());
        mail.setText(mensaje.cuerpo());
        javaMailSender.send(mail);
    }
}
