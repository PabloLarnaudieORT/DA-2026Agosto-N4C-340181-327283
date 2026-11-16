package uy.edu.ort.battleshipdemo.excepciones;

import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BatallaException.class)
    public ResponseEntity<String> manejarBatallaException(
            BatallaException error) {

        return ResponseEntity.status(299).body(error.getMessage());
    }
}