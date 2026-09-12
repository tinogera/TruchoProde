-- Aviso por mail de predicciones pendientes.
--
-- Se manda una hora antes de cada horario de arranque, a los usuarios que son miembros de algun
-- grupo y todavia no cargaron su prediccion. Como la tarea programada recorre una ventana de tiempo
-- cada pocos minutos, el mismo partido cae en varias corridas seguidas: esta tabla es lo que hace
-- que el aviso salga una sola vez.

ALTER TABLE usuario
    ADD COLUMN quiere_avisos BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE aviso_prediccion (
    usuario_id BIGINT      NOT NULL REFERENCES usuario (id) ON DELETE CASCADE,
    partido_id BIGINT      NOT NULL REFERENCES partido (id) ON DELETE CASCADE,
    enviado_en TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- La clave primaria compuesta ES la garantia de no duplicar: no es una bandera que el codigo
    -- consulta y se puede olvidar, es una fila que no puede existir dos veces.
    PRIMARY KEY (usuario_id, partido_id)
);

-- Para limpiar o auditar por partido.
CREATE INDEX aviso_prediccion_partido_idx ON aviso_prediccion (partido_id);
