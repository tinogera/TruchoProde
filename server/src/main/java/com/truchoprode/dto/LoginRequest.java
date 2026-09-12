package com.truchoprode.dto;

import jakarta.validation.constraints.NotBlank;

/** Un solo campo para las dos formas de entrar: nombre de usuario o mail. */
public record LoginRequest(@NotBlank String usuarioOEmail, @NotBlank String contrasena) {}
