package com.truchoprode.service;

import com.truchoprode.domain.TokenFirmado;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;

/**
 * Firma y valida los tokens. No conoce la base ni HTTP: solo sabe convertir un id de usuario en un
 * token y volver atras.
 *
 * <p>El token lleva unicamente el id en el "sub". El rol NO viaja adentro a proposito: lo lee el
 * filtro contra la base en cada request, asi una promocion a ADMIN toma efecto en el request
 * siguiente en vez de esperar a que el token venza.
 *
 * <p>El reloj se recibe en el constructor para que el vencimiento se pueda testear sin esperar.
 */
public class JwtService {

    private final SecretKey clave;
    private final int expiracionMinutos;
    private final Clock reloj;

    public JwtService(String secreto, int expiracionMinutos, Clock reloj) {
        this.clave = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        this.expiracionMinutos = expiracionMinutos;
        this.reloj = reloj;
    }

    public TokenFirmado firmar(Long usuarioId) {
        Instant ahora = reloj.instant();
        Instant vence = ahora.plus(expiracionMinutos, ChronoUnit.MINUTES);
        String token = Jwts.builder()
                .subject(String.valueOf(usuarioId))
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(vence))
                .signWith(clave)
                .compact();
        return new TokenFirmado(token, vence);
    }

    /**
     * Vacio si el token no sirve por cualquier motivo: firma ajena, vencido, mal formado o nulo.
     * Quien llama no necesita distinguir el caso; todos terminan en el mismo 401.
     */
    public Optional<Long> usuarioDe(String token) {
        try {
            Claims cuerpo = Jwts.parser()
                    .verifyWith(clave)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(Long.valueOf(cuerpo.getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            // IllegalArgumentException cubre el token nulo o vacio, que no es un JwtException.
            return Optional.empty();
        }
    }
}
