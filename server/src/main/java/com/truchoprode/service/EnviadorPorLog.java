package com.truchoprode.service;

import com.truchoprode.domain.MensajeDeAviso;
import lombok.extern.slf4j.Slf4j;

/**
 * Escribe el aviso en el log en vez de mandarlo. Es el transporte por defecto: en desarrollo se
 * puede probar todo el circuito sin credenciales y sin molestar a nadie.
 */
@Slf4j
public class EnviadorPorLog implements EnviadorDeAvisos {

    @Override
    public void enviar(MensajeDeAviso mensaje) {
        log.info(
                "[AVISO NO ENVIADO - transporte=log]\nPara: {}\nAsunto: {}\n{}",
                mensaje.destinatario(),
                mensaje.asunto(),
                mensaje.cuerpo());
    }
}
