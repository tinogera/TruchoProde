package com.truchoprode.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.truchoprode.domain.AvisoPendiente;
import com.truchoprode.domain.MensajeDeAviso;
import com.truchoprode.mapper.AvisoMapper;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Service de avisos de prediccion")
class AvisoDePrediccionServiceTest {

    private static final Instant AHORA = Instant.parse("2026-09-12T17:00:00Z");
    private static final Instant LAS_15 = Instant.parse("2026-09-12T18:00:00Z");
    private static final Instant LAS_17 = Instant.parse("2026-09-12T20:00:00Z");

    /** Guarda lo que le pidieron y devuelve lo que le cargaron. Sin base de datos. */
    private static final class MapperFalso implements AvisoMapper {
        private List<AvisoPendiente> pendientes = new ArrayList<>();
        private final List<String> marcados = new ArrayList<>();
        private Instant desdeRecibido;
        private Instant hastaRecibido;

        @Override
        public List<AvisoPendiente> pendientesEntre(Instant desde, Instant hasta) {
            this.desdeRecibido = desde;
            this.hastaRecibido = hasta;
            return pendientes;
        }

        @Override
        public void marcarEnviado(Long usuarioId, Long partidoId) {
            marcados.add(usuarioId + ":" + partidoId);
        }
    }

    /** Registra los mails en vez de mandarlos, y puede fallar a pedido. */
    private static final class EnviadorFalso implements EnviadorDeAvisos {
        private final List<MensajeDeAviso> enviados = new ArrayList<>();
        private String destinatarioQueFalla;

        @Override
        public void enviar(MensajeDeAviso mensaje) {
            if (mensaje.destinatario().equals(destinatarioQueFalla)) {
                throw new RuntimeException("el servidor de mail rechazo el envio");
            }
            enviados.add(mensaje);
        }
    }

    private final MapperFalso mapper = new MapperFalso();
    private final EnviadorFalso enviador = new EnviadorFalso();

    private final AvisoDePrediccionService service = new AvisoDePrediccionService(
            mapper,
            enviador,
            new RedactorDeAvisos(ZoneId.of("America/Argentina/Buenos_Aires"), "http://localhost"),
            60);

    private AvisoPendiente pendiente(long usuarioId, long partidoId, Instant comienzaEn) {
        AvisoPendiente aviso = new AvisoPendiente();
        aviso.setUsuarioId(usuarioId);
        aviso.setEmail("jugador" + usuarioId + "@ejemplo.com");
        aviso.setNombreUsuario("jugador" + usuarioId);
        aviso.setPartidoId(partidoId);
        aviso.setEquipoLocal("Local " + partidoId);
        aviso.setEquipoVisitante("Visitante " + partidoId);
        aviso.setComienzaEn(comienzaEn);
        return aviso;
    }

    @Test
    @DisplayName("pregunta por la ventana que va desde ahora hasta la anticipacion configurada")
    void preguntaPorLaVentanaConfigurada() {
        service.avisarPendientes(AHORA);

        assertThat(mapper.desdeRecibido).isEqualTo(AHORA);
        assertThat(mapper.hastaRecibido).isEqualTo(LAS_15);
    }

    @Test
    @DisplayName("manda un solo mail por tanda, aunque falten varios partidos")
    void mandaUnMailPorTanda() {
        mapper.pendientes = List.of(pendiente(1, 10, LAS_15), pendiente(1, 11, LAS_15));

        int enviados = service.avisarPendientes(AHORA);

        assertThat(enviados).isEqualTo(1);
        assertThat(enviador.enviados).hasSize(1);
        assertThat(enviador.enviados.getFirst().destinatario()).isEqualTo("jugador1@ejemplo.com");
    }

    @Test
    @DisplayName("dos horarios distintos del mismo usuario son dos mails")
    void dosHorariosSonDosMails() {
        mapper.pendientes = List.of(pendiente(1, 10, LAS_15), pendiente(1, 20, LAS_17));

        assertThat(service.avisarPendientes(AHORA)).isEqualTo(2);
    }

    @Test
    @DisplayName("marca todos los partidos de la tanda, no solo el primero")
    void marcaTodosLosPartidosDeLaTanda() {
        mapper.pendientes = List.of(pendiente(1, 10, LAS_15), pendiente(1, 11, LAS_15));

        service.avisarPendientes(AHORA);

        assertThat(mapper.marcados).containsExactly("1:10", "1:11");
    }

    @Test
    @DisplayName("si el mail falla no marca nada, para reintentar en la corrida siguiente")
    void siElMailFallaNoMarca() {
        enviador.destinatarioQueFalla = "jugador1@ejemplo.com";
        mapper.pendientes = List.of(pendiente(1, 10, LAS_15));

        assertThat(service.avisarPendientes(AHORA)).isZero();
        assertThat(mapper.marcados).isEmpty();
    }

    @Test
    @DisplayName("un usuario que falla no corta el lote")
    void unUsuarioQueFallaNoCortaElLote() {
        enviador.destinatarioQueFalla = "jugador1@ejemplo.com";
        mapper.pendientes = List.of(pendiente(1, 10, LAS_15), pendiente(2, 10, LAS_15));

        assertThat(service.avisarPendientes(AHORA)).isEqualTo(1);
        assertThat(enviador.enviados.getFirst().destinatario()).isEqualTo("jugador2@ejemplo.com");
        assertThat(mapper.marcados).containsExactly("2:10");
    }

    @Test
    @DisplayName("sin pendientes no manda nada")
    void sinPendientesNoMandaNada() {
        assertThat(service.avisarPendientes(AHORA)).isZero();
        assertThat(enviador.enviados).isEmpty();
    }
}
