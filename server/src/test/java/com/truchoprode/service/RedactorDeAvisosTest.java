package com.truchoprode.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.truchoprode.domain.AvisoPendiente;
import com.truchoprode.domain.MensajeDeAviso;
import com.truchoprode.domain.TandaDeAvisos;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Redactor de avisos")
class RedactorDeAvisosTest {

    /** 18:00 UTC son las 15:00 en Buenos Aires. */
    private static final Instant LAS_15_EN_BUENOS_AIRES = Instant.parse("2026-09-12T18:00:00Z");

    private static final ZoneId BUENOS_AIRES = ZoneId.of("America/Argentina/Buenos_Aires");

    private final RedactorDeAvisos redactor =
            new RedactorDeAvisos(BUENOS_AIRES, "http://localhost:5173");

    private AvisoPendiente partido(String local, String visitante) {
        AvisoPendiente aviso = new AvisoPendiente();
        aviso.setUsuarioId(1L);
        aviso.setEmail("santino@ejemplo.com");
        aviso.setNombreUsuario("santino");
        aviso.setPartidoId(10L);
        aviso.setEquipoLocal(local);
        aviso.setEquipoVisitante(visitante);
        aviso.setComienzaEn(LAS_15_EN_BUENOS_AIRES);
        return aviso;
    }

    private TandaDeAvisos tandaCon(AvisoPendiente... partidos) {
        return new TandaDeAvisos(
                1L, "santino@ejemplo.com", "santino", LAS_15_EN_BUENOS_AIRES, List.of(partidos));
    }

    @Test
    @DisplayName("el mail va al mail del usuario")
    void vaAlMailDelUsuario() {
        MensajeDeAviso mensaje = redactor.redactar(tandaCon(partido("Boca", "River")));

        assertThat(mensaje.destinatario()).isEqualTo("santino@ejemplo.com");
    }

    @Test
    @DisplayName("el asunto dice cuantas faltan y a que hora local arrancan")
    void elAsuntoDiceCuantasFaltanYAQueHora() {
        MensajeDeAviso mensaje = redactor.redactar(
                tandaCon(partido("Boca", "River"), partido("Racing", "Independiente")));

        assertThat(mensaje.asunto()).isEqualTo("Te faltan 2 predicciones (arrancan 15:00)");
    }

    @Test
    @DisplayName("con un solo partido el asunto va en singular")
    void conUnSoloPartidoElAsuntoVaEnSingular() {
        MensajeDeAviso mensaje = redactor.redactar(tandaCon(partido("Boca", "River")));

        assertThat(mensaje.asunto()).isEqualTo("Te falta 1 predicción (arranca 15:00)");
    }

    @Test
    @DisplayName("el cuerpo saluda por nombre y lista todos los partidos")
    void elCuerpoListaTodosLosPartidos() {
        MensajeDeAviso mensaje = redactor.redactar(
                tandaCon(partido("Boca", "River"), partido("Racing", "Independiente")));

        assertThat(mensaje.cuerpo())
                .contains("Hola santino")
                .contains("Boca - River")
                .contains("Racing - Independiente");
    }

    @Test
    @DisplayName("el cuerpo lleva el link para cargarlas")
    void elCuerpoLlevaElLink() {
        MensajeDeAviso mensaje = redactor.redactar(tandaCon(partido("Boca", "River")));

        assertThat(mensaje.cuerpo()).contains("http://localhost:5173/predicciones");
    }

    @Test
    @DisplayName("la hora sale en la zona configurada, no en UTC")
    void laHoraSaleEnLaZonaConfigurada() {
        RedactorDeAvisos enUtc = new RedactorDeAvisos(ZoneId.of("UTC"), "http://localhost:5173");

        MensajeDeAviso mensaje = enUtc.redactar(tandaCon(partido("Boca", "River")));

        assertThat(mensaje.asunto()).contains("18:00");
    }
}
