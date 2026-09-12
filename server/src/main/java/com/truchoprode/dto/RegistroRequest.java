package com.truchoprode.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * El rol no esta ni como campo: todo el que se registra nace JUGADOR y no hay forma de pedir otra
 * cosa desde afuera.
 */
public record RegistroRequest(
        @NotBlank
        @Size(min = 3, max = 30)
        @Pattern(
                regexp = "[A-Za-z0-9._]+",
                message = "solo letras, numeros, punto y guion bajo, sin espacios")
        String nombreUsuario,
        @NotBlank @Email @Size(max = 255) String email,
        // El maximo de 72 no es arbitrario: BCrypt ignora en silencio lo que pase de 72 bytes, asi
        // que sin el tope una contrasena larguisima se recortaria sin avisarle a nadie.
        @NotBlank @Size(min = 8, max = 72) String contrasena) {}
