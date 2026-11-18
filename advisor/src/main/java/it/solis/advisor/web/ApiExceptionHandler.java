package it.solis.advisor.web;

import it.solis.advisor.client.UpstreamException;
import it.solis.advisor.rules.InvalidRuleException;
import it.solis.advisor.service.RuleNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(RuleNotFoundException.class)
    ProblemDetail notFound(RuleNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(UpstreamException.class)
    ProblemDetail upstream(UpstreamException e) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, e.getMessage());
        problem.setTitle("Servizio non disponibile");
        return problem;
    }

    @ExceptionHandler(InvalidRuleException.class)
    ProblemDetail invalid(InvalidRuleException e) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
        problem.setTitle("Regola non valida");
        return problem;
    }
}
