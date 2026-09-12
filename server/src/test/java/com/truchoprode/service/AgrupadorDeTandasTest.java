package com.truchoprode.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.truchoprode.domain.AvisoPendiente;
import com.truchoprode.domain.TandaDeAvisos;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Agrupador de tandas")
class AgrupadorDeTandasTest {

    private static final Instant LAS_15 = Instant.parse("2026-09-12T18:00:00Z");
    private static final Instant LAS_17 = Instant.parse("2026-09-12T20:00:00Z");

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
    @DisplayName("varios partidos del mismo usuario a la misma hora son una sola tanda")
    void mismoUsuarioMismaHoraEsUnaTanda() {
        List<TandaDeAvisos> tandas = AgrupadorDeTandas.agrupar(
                List.of(pendiente(1, 10, LAS_15), pendiente(1, 11, LAS_15)));

        assertThat(tandas).hasSize(1);
        assertThat(tandas.getFirst().partidos()).hasSize(2);
        assertThat(tandas.getFirst().usuarioId()).isEqualTo(1L);
        assertThat(tandas.getFirst().comienzaEn()).isEqualTo(LAS_15);
    }

    @Test
    @DisplayName("el mismo usuario con dos horarios recibe dos tandas")
    void horariosDistintosSonTandasDistintas() {
        List<TandaDeAvisos> tandas = AgrupadorDeTandas.agrupar(
                List.of(pendiente(1, 10, LAS_15), pendiente(1, 20, LAS_17)));

        assertThat(tandas).hasSize(2);
        assertThat(tandas).extracting(TandaDeAvisos::comienzaEn).containsExactly(LAS_15, LAS_17);
    }

    @Test
    @DisplayName("dos usuarios en el mismo horario reciben una tanda cada uno")
    void cadaUsuarioTieneSuPropiaTanda() {
        List<TandaDeAvisos> tandas = AgrupadorDeTandas.agrupar(
                List.of(pendiente(1, 10, LAS_15), pendiente(2, 10, LAS_15)));

        assertThat(tandas).hasSize(2);
        assertThat(tandas).extracting(TandaDeAvisos::usuarioId).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("la tanda conserva el mail y el nombre del usuario")
    void conservaLosDatosDelDestinatario() {
        List<TandaDeAvisos> tandas = AgrupadorDeTandas.agrupar(List.of(pendiente(7, 10, LAS_15)));

        assertThat(tandas.getFirst().email()).isEqualTo("jugador7@ejemplo.com");
        assertThat(tandas.getFirst().nombreUsuario()).isEqualTo("jugador7");
    }

    @Test
    @DisplayName("sin pendientes no hay tandas")
    void sinPendientesNoHayTandas() {
        assertThat(AgrupadorDeTandas.agrupar(List.of())).isEmpty();
    }
}
