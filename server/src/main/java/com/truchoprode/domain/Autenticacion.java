package com.truchoprode.domain;

import java.time.Instant;

/** Lo que devuelven el registro y el login: el token, cuando vence, y de quien es. */
public record Autenticacion(String token, Instant expiraEn, Usuario usuario) {}
