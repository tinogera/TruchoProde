package com.truchoprode.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.truchoprode.domain.AvisoPendiente;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Corre contra la base real y deja todo como estaba: @Transactional hace rollback al terminar.
 * Necesita el contenedor levantado (podman start truchoprode-db).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Consulta de avisos pendientes")
class AvisoMapperTest {

    private static final Instant AHORA = Instant.parse("2026-09-12T17:00:00Z");
    private static final Instant DENTRO_DE_UNA_HORA = AHORA.plus(60, ChronoUnit.MINUTES);

    @Autowired private AvisoMapper mapper;
    @Autowired private JdbcTemplate jdbc;

    private long usuario(String nombre) {
        return jdbc.queryForObject(
                """
                INSERT INTO usuario (nombre_usuario, email, contrasena_hash)
                VALUES (?, ?, 'hash') RETURNING id
                """,
                Long.class,
                nombre,
                nombre + "@ejemplo.com");
    }

    private long grupoCon(long... usuarioIds) {
        long grupoId = jdbc.queryForObject(
                """
                INSERT INTO grupo (nombre, codigo_invitacion, creador_id)
                VALUES ('Los pibes', ?, ?) RETURNING id
                """,
                Long.class,
                "cod" + System.nanoTime() % 100000,
                usuarioIds[0]);
        for (long usuarioId : usuarioIds) {
            jdbc.update("INSERT INTO miembro (grupo_id, usuario_id) VALUES (?, ?)", grupoId, usuarioId);
        }
        return grupoId;
    }

    private long partido(Instant comienzaEn) {
        long jornadaId = jdbc.queryForObject(
                "INSERT INTO jornada (numero, nombre) VALUES (?, 'Fecha') RETURNING id",
                Long.class,
                (int) (System.nanoTime() % 1_000_000));
        return jdbc.queryForObject(
                """
                INSERT INTO partido (jornada_id, equipo_local, equipo_visitante, comienza_en)
                VALUES (?, 'Boca', 'River', ?) RETURNING id
                """,
                Long.class,
                jornadaId,
                java.sql.Timestamp.from(comienzaEn));
    }

    private List<AvisoPendiente> pendientes() {
        return mapper.pendientesEntre(AHORA, DENTRO_DE_UNA_HORA);
    }

    @Test
    @DisplayName("incluye al miembro de un grupo que no cargo su prediccion")
    void incluyeAlQueNoPredijo() {
        long usuarioId = usuario("santino");
        grupoCon(usuarioId);
        long partidoId = partido(AHORA.plus(30, ChronoUnit.MINUTES));

        assertThat(pendientes())
                .extracting(AvisoPendiente::getUsuarioId, AvisoPendiente::getPartidoId)
                .contains(org.assertj.core.api.Assertions.tuple(usuarioId, partidoId));
    }

    @Test
    @DisplayName("trae el mail, el nombre y los datos del partido")
    void traeLosDatosNecesariosParaElMail() {
        long usuarioId = usuario("pedro");
        grupoCon(usuarioId);
        partido(AHORA.plus(30, ChronoUnit.MINUTES));

        AvisoPendiente aviso = pendientes().stream()
                .filter(a -> a.getUsuarioId().equals(usuarioId))
                .findFirst()
                .orElseThrow();

        assertThat(aviso.getEmail()).isEqualTo("pedro@ejemplo.com");
        assertThat(aviso.getNombreUsuario()).isEqualTo("pedro");
        assertThat(aviso.getEquipoLocal()).isEqualTo("Boca");
        assertThat(aviso.getEquipoVisitante()).isEqualTo("River");
        assertThat(aviso.getComienzaEn()).isEqualTo(AHORA.plus(30, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("excluye al que ya cargo la prediccion")
    void excluyeAlQueYaPredijo() {
        long usuarioId = usuario("lucia");
        grupoCon(usuarioId);
        long partidoId = partido(AHORA.plus(30, ChronoUnit.MINUTES));
        jdbc.update(
                """
                INSERT INTO prediccion (usuario_id, partido_id, goles_local, goles_visitante)
                VALUES (?, ?, 2, 1)
                """,
                usuarioId,
                partidoId);

        assertThat(pendientes()).noneMatch(a -> a.getUsuarioId().equals(usuarioId));
    }

    @Test
    @DisplayName("excluye al que no esta en ningun grupo")
    void excluyeAlQueNoEstaEnNingunGrupo() {
        long usuarioId = usuario("solitario");
        partido(AHORA.plus(30, ChronoUnit.MINUTES));

        assertThat(pendientes()).noneMatch(a -> a.getUsuarioId().equals(usuarioId));
    }

    @Test
    @DisplayName("excluye al que ya fue avisado por ese partido")
    void excluyeAlYaAvisado() {
        long usuarioId = usuario("avisado");
        grupoCon(usuarioId);
        long partidoId = partido(AHORA.plus(30, ChronoUnit.MINUTES));
        mapper.marcarEnviado(usuarioId, partidoId);

        assertThat(pendientes()).noneMatch(a -> a.getUsuarioId().equals(usuarioId));
    }

    @Test
    @DisplayName("excluye al que apago los avisos")
    void excluyeAlQueApagoLosAvisos() {
        long usuarioId = usuario("callado");
        grupoCon(usuarioId);
        partido(AHORA.plus(30, ChronoUnit.MINUTES));
        jdbc.update("UPDATE usuario SET quiere_avisos = FALSE WHERE id = ?", usuarioId);

        assertThat(pendientes()).noneMatch(a -> a.getUsuarioId().equals(usuarioId));
    }

    @Test
    @DisplayName("excluye los partidos que arrancan despues de la ventana")
    void excluyeLosPartidosLejanos() {
        long usuarioId = usuario("temprano");
        grupoCon(usuarioId);
        long partidoId = partido(AHORA.plus(90, ChronoUnit.MINUTES));

        assertThat(pendientes()).noneMatch(a -> a.getPartidoId().equals(partidoId));
    }

    @Test
    @DisplayName("excluye los partidos que ya empezaron")
    void excluyeLosPartidosYaEmpezados() {
        long usuarioId = usuario("tarde");
        grupoCon(usuarioId);
        long partidoId = partido(AHORA.minus(10, ChronoUnit.MINUTES));

        assertThat(pendientes()).noneMatch(a -> a.getPartidoId().equals(partidoId));
    }
}
