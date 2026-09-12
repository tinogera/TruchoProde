package com.truchoprode.domain;

/**
 * Quien esta haciendo el pedido, ya verificado. Es lo que los controllers reciben con
 * AuthenticationPrincipal, y la razon por la que el id del usuario nunca llega desde el cliente: si
 * viniera en el cuerpo o en la URL, cualquiera podria predecir o transferir en nombre de otro.
 */
public record UsuarioAutenticado(Long id, String nombreUsuario, Rol rol) {}
