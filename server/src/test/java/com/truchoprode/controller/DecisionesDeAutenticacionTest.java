package com.truchoprode.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.truchoprode.domain.Autenticacion;
import com.truchoprode.service.AutenticacionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Estos dos tests existen para que las decisiones no se deshagan sin que nadie se de cuenta. Si
 * alguien "optimiza" el filtro metiendo el rol adentro del token, el segundo se pone rojo.
 *
 * <p>OJO: a diferencia del resto de los tests de integracion, este NO lleva @Transactional, y es a
 * proposito. Con una transaccion compartida los dos pedidos caen en la misma SqlSession de MyBatis
 * y el cache de primer nivel devuelve el usuario que ya habia leido, ignorando el UPDATE del medio:
 * el test daba rojo con el codigo funcionando bien. En produccion cada request abre su propia
 * sesion, asi que sin @Transactional el test reproduce el escenario de verdad. El precio es limpiar
 * a mano lo que se creo, que es lo que hace el @AfterEach.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Las decisiones de la autenticacion")
class DecisionesDeAutenticacionTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AutenticacionService autenticacion;
    @Autowired private JdbcTemplate jdbc;

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM usuario WHERE nombre_usuario IN ('fantasma', 'asciende')");
    }

    @Test
    @DisplayName("el token de un usuario borrado deja de servir")
    void elTokenDeUnBorradoNoSirve() throws Exception {
        Autenticacion alta =
                autenticacion.registrar("fantasma", "fantasma@ejemplo.com", "clave1234");
        jdbc.update("DELETE FROM usuario WHERE id = ?", alta.usuario().getId());

        mockMvc.perform(get("/api/auth/yo").header("Authorization", "Bearer " + alta.token()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("el rol se lee de la base: promover a ADMIN no necesita un token nuevo")
    void elRolSaleDeLaBase() throws Exception {
        Autenticacion alta =
                autenticacion.registrar("asciende", "asciende@ejemplo.com", "clave1234");
        String tokenDeCuandoEraJugador = alta.token();

        mockMvc.perform(get("/api/auth/yo")
                        .header("Authorization", "Bearer " + tokenDeCuandoEraJugador))
                .andExpect(jsonPath("$.rol").value("JUGADOR"));

        jdbc.update("UPDATE usuario SET rol = 'ADMIN' WHERE id = ?", alta.usuario().getId());

        // El MISMO token de antes, sin volver a loguearse.
        mockMvc.perform(get("/api/auth/yo")
                        .header("Authorization", "Bearer " + tokenDeCuandoEraJugador))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("ADMIN"));
    }
}
