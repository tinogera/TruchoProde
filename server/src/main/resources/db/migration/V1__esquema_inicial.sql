-- Esquema inicial de TruchoProde.
--
-- Decisiones que la base hace cumplir por sí sola (no dependen de que el código se acuerde):
--   * una sola predicción por usuario y partido, sin grupo (los puntos por predicciones son globales);
--   * una transferencia solo puede ir entre dos miembros del MISMO grupo;
--   * no existe la transferencia de 0 puntos ni la de puntos negativos.
--
-- El saldo de cada miembro NO se guarda: se calcula como
--   puntos_por_predicciones (global) + recibidos_en_el_grupo - transferidos_en_el_grupo.
-- Por eso `miembro` no tiene columna de score y el "no podés quedar en negativo" lo garantiza el
-- service al recortar la transferencia al saldo disponible.


-- ---------------------------------------------------------------------------
-- usuario
-- ---------------------------------------------------------------------------
CREATE TABLE usuario (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre_usuario  VARCHAR(30)  NOT NULL,
    email           VARCHAR(255) NOT NULL,
    contrasena_hash VARCHAR(100) NOT NULL,
    -- Rol de plataforma, no de grupo: ADMIN es quien crea jornadas y carga partidos y resultados.
    rol             VARCHAR(10)  NOT NULL DEFAULT 'JUGADOR',
    creado_en       TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT usuario_rol_valido CHECK (rol IN ('JUGADOR', 'ADMIN'))
);

-- Únicos sin distinguir mayúsculas: a un grupo se entra escribiendo el nombre de usuario, así que
-- no puede haber un "Santino" y un "santino" distintos.
CREATE UNIQUE INDEX usuario_nombre_usuario_unico ON usuario (lower(nombre_usuario));
CREATE UNIQUE INDEX usuario_email_unico          ON usuario (lower(email));


-- ---------------------------------------------------------------------------
-- grupo
-- ---------------------------------------------------------------------------
CREATE TABLE grupo (
    id                BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre            VARCHAR(60) NOT NULL,
    -- Sirve para las dos formas de invitación que no son por nombre de usuario: el código se tipea
    -- y el link lo lleva adentro. Es el mismo dato, no hace falta guardarlo dos veces.
    codigo_invitacion VARCHAR(16) NOT NULL UNIQUE,
    creador_id        BIGINT      NOT NULL REFERENCES usuario (id),
    creado_en         TIMESTAMPTZ NOT NULL DEFAULT now()
);


-- ---------------------------------------------------------------------------
-- miembro  (relación usuario-grupo)
-- ---------------------------------------------------------------------------
CREATE TABLE miembro (
    grupo_id   BIGINT      NOT NULL REFERENCES grupo (id)   ON DELETE CASCADE,
    usuario_id BIGINT      NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    se_unio_en TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- La clave primaria compuesta hace dos cosas: impide entrar dos veces al mismo grupo y es el
    -- destino de las claves foráneas de `transferencia`.
    PRIMARY KEY (grupo_id, usuario_id)
);

-- Para "¿en qué grupos estoy?", que va por el otro lado de la clave primaria.
CREATE INDEX miembro_usuario_idx ON miembro (usuario_id);


-- ---------------------------------------------------------------------------
-- jornada
-- ---------------------------------------------------------------------------
CREATE TABLE jornada (
    id        BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    numero    INTEGER     NOT NULL UNIQUE,
    nombre    VARCHAR(60) NOT NULL,
    creada_en TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT jornada_numero_positivo CHECK (numero > 0)
);


-- ---------------------------------------------------------------------------
-- partido
-- ---------------------------------------------------------------------------
CREATE TABLE partido (
    id               BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    jornada_id       BIGINT      NOT NULL REFERENCES jornada (id) ON DELETE CASCADE,
    equipo_local     VARCHAR(60) NOT NULL,
    equipo_visitante VARCHAR(60) NOT NULL,
    comienza_en      TIMESTAMPTZ NOT NULL,
    -- Resultado: NULL mientras el admin no lo cargó.
    goles_local      SMALLINT,
    goles_visitante  SMALLINT,

    CONSTRAINT partido_equipos_distintos CHECK (equipo_local <> equipo_visitante),
    CONSTRAINT partido_goles_no_negativos CHECK (
        (goles_local     IS NULL OR goles_local     >= 0) AND
        (goles_visitante IS NULL OR goles_visitante >= 0)
    ),
    -- O están los dos goles o no está ninguno: nunca un resultado a medias.
    CONSTRAINT partido_resultado_completo CHECK ((goles_local IS NULL) = (goles_visitante IS NULL))
);

CREATE INDEX partido_jornada_idx ON partido (jornada_id);


-- ---------------------------------------------------------------------------
-- prediccion
-- ---------------------------------------------------------------------------
CREATE TABLE prediccion (
    id              BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    usuario_id      BIGINT      NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    partido_id      BIGINT      NOT NULL REFERENCES partido (id) ON DELETE CASCADE,
    goles_local     SMALLINT    NOT NULL,
    goles_visitante SMALLINT    NOT NULL,
    -- Puntos que dio esta predicción: NULL hasta que el partido tenga resultado.
    -- 3 = marcador exacto, 2 = acertó quién ganó (o el empate), 0 = erró.
    puntos          SMALLINT,
    creada_en       TIMESTAMPTZ NOT NULL DEFAULT now(),
    actualizada_en  TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- LA regla del dominio: una predicción por usuario y partido. Fijate que no hay grupo_id;
    -- la misma predicción cuenta igual en todos los grupos del usuario.
    CONSTRAINT prediccion_unica_por_partido UNIQUE (usuario_id, partido_id),
    CONSTRAINT prediccion_goles_no_negativos CHECK (goles_local >= 0 AND goles_visitante >= 0),
    CONSTRAINT prediccion_puntos_validos CHECK (puntos IS NULL OR puntos IN (0, 2, 3))
);

CREATE INDEX prediccion_partido_idx ON prediccion (partido_id);


-- ---------------------------------------------------------------------------
-- transferencia
-- ---------------------------------------------------------------------------
CREATE TABLE transferencia (
    id          BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    grupo_id    BIGINT      NOT NULL,
    emisor_id   BIGINT      NOT NULL,
    receptor_id BIGINT      NOT NULL,
    puntos      INTEGER     NOT NULL,
    creada_en   TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Las dos foráneas apuntan a (grupo_id, usuario_id) de `miembro` usando la MISMA columna
    -- grupo_id, así que la base vuelve imposible transferirle a alguien de otro grupo.
    CONSTRAINT transferencia_emisor_miembro   FOREIGN KEY (grupo_id, emisor_id)
        REFERENCES miembro (grupo_id, usuario_id),
    CONSTRAINT transferencia_receptor_miembro FOREIGN KEY (grupo_id, receptor_id)
        REFERENCES miembro (grupo_id, usuario_id),

    CONSTRAINT transferencia_distinto_receptor CHECK (emisor_id <> receptor_id),
    -- "Si el saldo ya es 0 no se registra nada" queda garantizado acá: no existe la fila de 0 puntos.
    CONSTRAINT transferencia_puntos_positivos CHECK (puntos > 0)
);

-- Las dos consultas del cálculo del saldo: lo que mandé y lo que recibí en un grupo.
CREATE INDEX transferencia_emisor_idx   ON transferencia (grupo_id, emisor_id);
CREATE INDEX transferencia_receptor_idx ON transferencia (grupo_id, receptor_id);
