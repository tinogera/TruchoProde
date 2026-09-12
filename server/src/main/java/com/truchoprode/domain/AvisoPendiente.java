package com.truchoprode.domain;

import java.time.Instant;
import lombok.Data;

/**
 * Una fila de la consulta de avisos: un usuario que todavia no cargo la prediccion de un partido
 * que esta por empezar. No es una entidad de la base, es lo que devuelve el cruce.
 */
@Data
public class AvisoPendiente {

    private Long usuarioId;
    private String email;
    private String nombreUsuario;
    private Long partidoId;
    private String equipoLocal;
    private String equipoVisitante;
    private Instant comienzaEn;
}
