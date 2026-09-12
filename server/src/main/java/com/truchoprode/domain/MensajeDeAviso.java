package com.truchoprode.domain;

/** Un mail ya escrito, listo para que lo despache un {@code EnviadorDeAvisos}. */
public record MensajeDeAviso(String destinatario, String asunto, String cuerpo) {}
