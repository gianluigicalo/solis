package it.solis.meteo.web;

import it.solis.meteo.client.OpenMeteoUnavailableException;
import it.solis.meteo.service.UnknownPlaneException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(OpenMeteoUnavailableException.class)
    ProblemDetail openMeteoUnavailable(OpenMeteoUnavailableException e) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, e.getMessage());
        problem.setTitle("Open-Meteo non disponibile");
        return problem;
    }

    @ExceptionHandler(UnknownPlaneException.class)
    ProblemDetail unknownPlane(UnknownPlaneException e) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
        problem.setTitle("Falda sconosciuta");
        return problem;
    }
}
