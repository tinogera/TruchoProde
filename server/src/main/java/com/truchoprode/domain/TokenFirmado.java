package com.truchoprode.domain;

import java.time.Instant;

/** Un token ya firmado y el momento en que deja de servir. */
public record TokenFirmado(String token, Instant expiraEn) {}
