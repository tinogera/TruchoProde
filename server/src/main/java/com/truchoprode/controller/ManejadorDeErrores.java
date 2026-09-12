package com.truchoprode.controller;

import com.truchoprode.exception.TruchoProdeException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce los errores del dominio a ProblemDetail. El "codigo" es lo que el frontend mira para
 * decidir que mostrar: el texto del mensaje puede cambiar, el codigo no.
 */
@RestControllerAdvice
public class ManejadorDeErrores {

    @ExceptionHandler(TruchoProdeException.class)
    public ProblemDetail deDominio(TruchoProdeException e) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(
                HttpStatusCode.valueOf(e.estadoHttp()), e.getMessage());
        problema.setProperty("codigo", e.codigo());
        return problema;
    }
}
