package com.truchoprode.service;

import com.truchoprode.domain.AvisoPendiente;
import com.truchoprode.domain.TandaDeAvisos;
import com.truchoprode.mapper.AvisoMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

/**
 * Avisa por mail a los que no cargaron su prediccion de un partido que esta por empezar.
 *
 * <p>Trabaja sobre una ventana de tiempo, no sobre un instante exacto: si la aplicacion estuvo
 * apagada, al volver manda lo que todavia sirva. Que un mismo partido caiga en varias corridas
 * seguidas no es problema, porque {@code aviso_prediccion} deja constancia de cada envio.
 */
@Slf4j
public class AvisoDePrediccionService {

    private final AvisoMapper mapper;
    private final EnviadorDeAvisos enviador;
    private final RedactorDeAvisos redactor;
    private final int anticipacionMinutos;

    public AvisoDePrediccionService(
            AvisoMapper mapper,
            EnviadorDeAvisos enviador,
            RedactorDeAvisos redactor,
            int anticipacionMinutos) {
        this.mapper = mapper;
        this.enviador = enviador;
        this.redactor = redactor;
        this.anticipacionMinutos = anticipacionMinutos;
    }

    /** @return cuantos mails salieron. */
    public int avisarPendientes(Instant ahora) {
        Instant hasta = ahora.plus(anticipacionMinutos, ChronoUnit.MINUTES);
        List<TandaDeAvisos> tandas =
                AgrupadorDeTandas.agrupar(mapper.pendientesEntre(ahora, hasta));

        int enviados = 0;
        for (TandaDeAvisos tanda : tandas) {
            if (avisar(tanda)) {
                enviados++;
            }
        }
        return enviados;
    }

    private boolean avisar(TandaDeAvisos tanda) {
        try {
            enviador.enviar(redactor.redactar(tanda));
        } catch (RuntimeException e) {
            // Un destinatario que falla no corta el lote. Como no se marca nada, la proxima
            // corrida lo vuelve a intentar.
            log.warn("No se pudo avisar a {}: {}", tanda.email(), e.getMessage());
            return false;
        }

        for (AvisoPendiente partido : tanda.partidos()) {
            mapper.marcarEnviado(tanda.usuarioId(), partido.getPartidoId());
        }
        return true;
    }
}
