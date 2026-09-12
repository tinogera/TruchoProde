package com.truchoprode.domain;

import java.time.Instant;
import java.util.List;

/**
 * Todo lo que le falta a un usuario para un mismo horario de arranque. Una tanda es un mail:
 * si a las 15:00 arrancan cuatro partidos sin predecir, es un solo aviso con los cuatro.
 */
public record TandaDeAvisos(
        Long usuarioId,
        String email,
        String nombreUsuario,
        Instant comienzaEn,
        List<AvisoPendiente> partidos) {}
