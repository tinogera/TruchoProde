package com.truchoprode.service;

import com.truchoprode.domain.MensajeDeAviso;

/**
 * Despacha un mail ya redactado. Hay dos implementaciones: una escribe en el log (desarrollo) y
 * la otra manda por SMTP. El service no sabe cual esta usando.
 */
public interface EnviadorDeAvisos {

    void enviar(MensajeDeAviso mensaje);
}
