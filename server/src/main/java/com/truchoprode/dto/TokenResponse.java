package com.truchoprode.dto;

import com.truchoprode.domain.Autenticacion;
import java.time.Instant;

/**
 * expiraEn es el instante de vencimiento en ISO-8601 UTC, no una cantidad de minutos: asi el front
 * lo compara contra el reloj del navegador sin tener que saber cuando se emitio el token.
 */
public record TokenResponse(String token, Instant expiraEn, UsuarioResponse usuario) {

    public static TokenResponse de(Autenticacion autenticacion) {
        return new TokenResponse(
                autenticacion.token(),
                autenticacion.expiraEn(),
                UsuarioResponse.de(autenticacion.usuario()));
    }
}
