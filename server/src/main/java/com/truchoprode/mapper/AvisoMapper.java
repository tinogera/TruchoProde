package com.truchoprode.mapper;

import com.truchoprode.domain.AvisoPendiente;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AvisoMapper {

    /** Usuarios que no cargaron prediccion para partidos que arrancan dentro de la ventana. */
    List<AvisoPendiente> pendientesEntre(
            @Param("desde") Instant desde, @Param("hasta") Instant hasta);

    /** Deja constancia de que a este usuario ya se le aviso por este partido. */
    void marcarEnviado(@Param("usuarioId") Long usuarioId, @Param("partidoId") Long partidoId);
}
