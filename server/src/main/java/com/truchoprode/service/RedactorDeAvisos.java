package com.truchoprode.service;

import com.truchoprode.domain.AvisoPendiente;
import com.truchoprode.domain.MensajeDeAviso;
import com.truchoprode.domain.TandaDeAvisos;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

/**
 * Escribe el mail de una tanda. No sabe mandarlo: solo arma el texto, asi se puede probar sin
 * tocar un servidor de correo.
 */
public class RedactorDeAvisos {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final ZoneId zona;
    private final String urlBase;

    public RedactorDeAvisos(ZoneId zona, String urlBase) {
        this.zona = zona;
        this.urlBase = urlBase;
    }

    public MensajeDeAviso redactar(TandaDeAvisos tanda) {
        int cuantos = tanda.partidos().size();
        String hora = HORA.withZone(zona).format(tanda.comienzaEn());
        boolean uno = cuantos == 1;

        String asunto = uno
                ? "Te falta 1 predicción (arranca " + hora + ")"
                : "Te faltan " + cuantos + " predicciones (arrancan " + hora + ")";

        String listado = tanda.partidos().stream()
                .map(RedactorDeAvisos::comoLinea)
                .collect(Collectors.joining("\n"));

        String cuerpo = """
                Hola %s,

                %s a las %s y todavía no cargaste %s:

                %s

                Cargalas acá: %s/predicciones

                Para dejar de recibir estos avisos, apagalos desde tu perfil.
                """
                .formatted(
                        tanda.nombreUsuario(),
                        uno ? "Arranca" : "Arrancan",
                        hora,
                        uno ? "tu predicción" : "tus predicciones",
                        listado,
                        urlBase);

        return new MensajeDeAviso(tanda.email(), asunto, cuerpo);
    }

    private static String comoLinea(AvisoPendiente partido) {
        return "  " + partido.getEquipoLocal() + " - " + partido.getEquipoVisitante();
    }
}
