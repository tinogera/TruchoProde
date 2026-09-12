package com.truchoprode.service;

import com.truchoprode.domain.AvisoPendiente;
import com.truchoprode.domain.TandaDeAvisos;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Convierte las filas sueltas de la consulta en tandas: un usuario y un horario de arranque.
 * Cada tanda termina siendo un mail.
 */
public final class AgrupadorDeTandas {

    private AgrupadorDeTandas() {}

    public static List<TandaDeAvisos> agrupar(List<AvisoPendiente> pendientes) {
        Map<Clave, List<AvisoPendiente>> porTanda = new LinkedHashMap<>();

        pendientes.stream()
                .sorted(Comparator.comparing(AvisoPendiente::getComienzaEn)
                        .thenComparing(AvisoPendiente::getUsuarioId))
                .forEach(pendiente -> porTanda
                        .computeIfAbsent(
                                new Clave(pendiente.getUsuarioId(), pendiente.getComienzaEn()),
                                clave -> new ArrayList<>())
                        .add(pendiente));

        return porTanda.values().stream().map(AgrupadorDeTandas::aTanda).toList();
    }

    private static TandaDeAvisos aTanda(List<AvisoPendiente> partidos) {
        AvisoPendiente primero = partidos.getFirst();
        return new TandaDeAvisos(
                primero.getUsuarioId(),
                primero.getEmail(),
                primero.getNombreUsuario(),
                primero.getComienzaEn(),
                List.copyOf(partidos));
    }

    private record Clave(Long usuarioId, Instant comienzaEn) {}
}
