package com.truchoprode.mapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.truchoprode.domain.Rol;
import com.truchoprode.domain.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Corre contra la base real y deja todo como estaba: @Transactional hace rollback al terminar.
 * Necesita el contenedor levantado (podman start truchoprode-db).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("Acceso a usuarios")
class UsuarioMapperTest {

    @Autowired private UsuarioMapper mapper;

    private Usuario nuevo(String nombre, String email) {
        Usuario usuario = new Usuario();
        usuario.setNombreUsuario(nombre);
        usuario.setEmail(email);
        usuario.setContrasenaHash("$2a$10$hashfalsoquenoimportaparaestetest");
        usuario.setRol(Rol.JUGADOR);
        return usuario;
    }

    @Test
    @DisplayName("al insertar completa el id generado")
    void insertarCompletaElId() {
        Usuario usuario = nuevo("santino", "santino@ejemplo.com");

        mapper.insertar(usuario);

        assertThat(usuario.getId()).isNotNull();
    }

    @Test
    @DisplayName("lo recupera por id con todos sus datos")
    void loRecuperaPorId() {
        Usuario usuario = nuevo("pedro", "pedro@ejemplo.com");
        mapper.insertar(usuario);

        Usuario traido = mapper.buscarPorId(usuario.getId());

        assertThat(traido.getNombreUsuario()).isEqualTo("pedro");
        assertThat(traido.getEmail()).isEqualTo("pedro@ejemplo.com");
        assertThat(traido.getContrasenaHash()).isEqualTo(usuario.getContrasenaHash());
        assertThat(traido.getRol()).isEqualTo(Rol.JUGADOR);
        assertThat(traido.getCreadoEn()).isNotNull();
    }

    @Test
    @DisplayName("la misma consulta lo encuentra por nombre de usuario y por email")
    void loEncuentraPorNombreYPorEmail() {
        Usuario usuario = nuevo("lucia", "lucia@ejemplo.com");
        mapper.insertar(usuario);

        assertThat(mapper.buscarPorNombreOEmail("lucia").getId()).isEqualTo(usuario.getId());
        assertThat(mapper.buscarPorNombreOEmail("lucia@ejemplo.com").getId())
                .isEqualTo(usuario.getId());
    }

    @Test
    @DisplayName("lo encuentra sin importar las mayusculas")
    void loEncuentraIgnorandoMayusculas() {
        Usuario usuario = nuevo("tinogera", "tino@ejemplo.com");
        mapper.insertar(usuario);

        assertThat(mapper.buscarPorNombreOEmail("TinoGera").getId()).isEqualTo(usuario.getId());
        assertThat(mapper.buscarPorNombreOEmail("Tino@Ejemplo.COM").getId())
                .isEqualTo(usuario.getId());
    }

    @Test
    @DisplayName("devuelve null si no hay nadie con ese nombre ni ese mail")
    void devuelveNullSiNoExiste() {
        assertThat(mapper.buscarPorNombreOEmail("nadie")).isNull();
    }

    @Test
    @DisplayName("dice si el nombre o el mail ya estan tomados, ignorando mayusculas")
    void diceSiYaEstanTomados() {
        mapper.insertar(nuevo("ocupado", "ocupado@ejemplo.com"));

        assertThat(mapper.existeNombreDeUsuario("ocupado")).isTrue();
        assertThat(mapper.existeNombreDeUsuario("OcUpAdO")).isTrue();
        assertThat(mapper.existeNombreDeUsuario("libre")).isFalse();
        assertThat(mapper.existeEmail("OCUPADO@ejemplo.com")).isTrue();
        assertThat(mapper.existeEmail("libre@ejemplo.com")).isFalse();
    }

    @Test
    @DisplayName("la base rechaza el nombre repetido aunque cambie la capitalizacion")
    void laBaseRechazaElNombreRepetido() {
        mapper.insertar(nuevo("repetido", "uno@ejemplo.com"));

        assertThatThrownBy(() -> mapper.insertar(nuevo("REPETIDO", "dos@ejemplo.com")))
                .isInstanceOf(DuplicateKeyException.class);
    }
}
