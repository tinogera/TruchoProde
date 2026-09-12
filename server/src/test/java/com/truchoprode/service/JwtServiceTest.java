package com.truchoprode.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.truchoprode.domain.TokenFirmado;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Firma y validacion de tokens")
class JwtServiceTest {

    private static final String SECRETO = "un-secreto-de-prueba-largo-de-mas-de-32-bytes-seguro";
    private static final String OTRO_SECRETO = "otro-secreto-distinto-igual-de-largo-para-la-prueba";

    private final JwtService jwt = new JwtService(SECRETO, 120, Clock.systemUTC());

    @Test
    @DisplayName("un token recien firmado devuelve el id del usuario")
    void elTokenDevuelveElUsuario() {
        TokenFirmado firmado = jwt.firmar(42L);

        assertThat(jwt.usuarioDe(firmado.token())).contains(42L);
    }

    @Test
    @DisplayName("el token dice cuando vence, 120 minutos despues de emitirse")
    void diceCuandoVence() {
        Instant emitido = Instant.parse("2026-09-12T18:00:00Z");
        JwtService conRelojFijo = new JwtService(SECRETO, 120, Clock.fixed(emitido, ZoneOffset.UTC));

        TokenFirmado firmado = conRelojFijo.firmar(42L);

        assertThat(firmado.expiraEn()).isEqualTo(emitido.plus(120, ChronoUnit.MINUTES));
    }

    @Test
    @DisplayName("rechaza un token vencido")
    void rechazaElVencido() {
        Clock haceTresHoras =
                Clock.fixed(Instant.now().minus(3, ChronoUnit.HOURS), ZoneOffset.UTC);
        String vencido = new JwtService(SECRETO, 120, haceTresHoras).firmar(42L).token();

        assertThat(jwt.usuarioDe(vencido)).isEmpty();
    }

    @Test
    @DisplayName("rechaza un token firmado con otro secreto")
    void rechazaOtraFirma() {
        String ajeno = new JwtService(OTRO_SECRETO, 120, Clock.systemUTC()).firmar(42L).token();

        assertThat(jwt.usuarioDe(ajeno)).isEmpty();
    }

    @Test
    @DisplayName("rechaza basura sin explotar")
    void rechazaBasura() {
        assertThat(jwt.usuarioDe("no-es-un-token")).isEqualTo(Optional.empty());
        assertThat(jwt.usuarioDe("")).isEqualTo(Optional.empty());
        assertThat(jwt.usuarioDe(null)).isEqualTo(Optional.empty());
    }
}
