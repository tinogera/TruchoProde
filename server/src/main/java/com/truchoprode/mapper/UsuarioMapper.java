package com.truchoprode.mapper;

import com.truchoprode.domain.Usuario;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UsuarioMapper {

    /** Inserta y completa el id generado sobre el objeto recibido. */
    void insertar(Usuario usuario);

    /** Null si no existe. */
    Usuario buscarPorId(@Param("id") Long id);

    /** Una sola consulta para las dos formas de loguearse. Null si no existe. */
    Usuario buscarPorNombreOEmail(@Param("identificador") String identificador);

    boolean existeNombreDeUsuario(@Param("nombreUsuario") String nombreUsuario);

    boolean existeEmail(@Param("email") String email);
}
