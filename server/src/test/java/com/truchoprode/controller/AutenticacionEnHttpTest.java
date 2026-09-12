package com.truchoprode.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Registro y login por HTTP")
class AutenticacionEnHttpTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;

    private String cuerpoDeRegistro(String nombre, String email, String contrasena)
            throws Exception {
        return json.writeValueAsString(
                java.util.Map.of("nombreUsuario", nombre, "email", email,
                        "contrasena", contrasena));
    }

    private String registrar(String nombre) throws Exception {
        String respuesta = mockMvc
                .perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoDeRegistro(nombre, nombre + "@ejemplo.com", "clave1234")))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json.readTree(respuesta).get("token").asString();
    }

    @Test
    @DisplayName("el registro devuelve 201 con el token y el usuario")
    void elRegistroDevuelveToken() throws Exception {
        mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoDeRegistro("santino", "santino@ejemplo.com", "clave1234")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.expiraEn").isNotEmpty())
                .andExpect(jsonPath("$.usuario.nombreUsuario").value("santino"))
                .andExpect(jsonPath("$.usuario.rol").value("JUGADOR"));
    }

    @Test
    @DisplayName("el registro nunca devuelve el hash de la contrasena")
    void elRegistroNoFiltraElHash() throws Exception {
        String respuesta = mockMvc
                .perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoDeRegistro("discreto", "discreto@ejemplo.com", "clave1234")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        org.assertj.core.api.Assertions.assertThat(respuesta)
                .doesNotContain("contrasena")
                .doesNotContain("$2a$");
    }

    @Test
    @DisplayName("el nombre repetido da 409 con su codigo")
    void elNombreRepetidoDa409() throws Exception {
        registrar("repetido");

        mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoDeRegistro("repetido", "otro@ejemplo.com", "clave1234")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("NOMBRE_DE_USUARIO_TOMADO"));
    }

    @Test
    @DisplayName("el cuerpo invalido da 400")
    void elCuerpoInvalidoDa400() throws Exception {
        mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoDeRegistro("x", "no-es-un-mail", "corta")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("la contrasena de mas de 72 caracteres da 400 en vez de recortarse callada")
    void laContrasenaLarguisimaDa400() throws Exception {
        mockMvc.perform(post("/api/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoDeRegistro("largo", "largo@ejemplo.com", "a".repeat(73))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("el login anda con el nombre y con el mail")
    void elLoginAndaConLosDos() throws Exception {
        registrar("santino");

        for (String identificador : new String[] {"santino", "santino@ejemplo.com"}) {
            mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(java.util.Map.of(
                                    "usuarioOEmail", identificador, "contrasena", "clave1234"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isNotEmpty());
        }
    }

    @Test
    @DisplayName("las credenciales mal dan 401 sin decir cual de los dos fallo")
    void lasCredencialesMalDan401() throws Exception {
        registrar("santino");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of(
                                "usuarioOEmail", "santino", "contrasena", "otraclave"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"));
    }

    @Test
    @DisplayName("yo sin token da 401")
    void yoSinTokenDa401() throws Exception {
        mockMvc.perform(get("/api/auth/yo")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("yo con token devuelve quien soy")
    void yoConTokenDiceQuienSoy() throws Exception {
        String token = registrar("santino");

        mockMvc.perform(get("/api/auth/yo").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreUsuario").value("santino"))
                .andExpect(jsonPath("$.rol").value("JUGADOR"));
    }
}
