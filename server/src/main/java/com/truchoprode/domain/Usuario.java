package com.truchoprode.domain;

import java.time.Instant;
import lombok.Data;

/** Un jugador registrado. El score no vive aca: es por grupo y vive en Miembro. */
@Data
public class Usuario {

    private Long id;
    private String nombreUsuario;
    private String email;
    private String contrasenaHash;
    private Rol rol;
    private Instant creadoEn;
}
