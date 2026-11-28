package bo.edu.uatf.activos;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.LinkedHashMap;

@RestControllerAdvice
public class Errores {
    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ProblemDetail restricciones(jakarta.validation.ConstraintViolationException error) {
        var problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Revisa los campos del activo");
        var campos = new LinkedHashMap<String, String>();
        error.getConstraintViolations().forEach(e -> campos.put(e.getPropertyPath().toString(), e.getMessage()));
        problema.setProperty("campos", campos);
        return problema;
    }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail conflicto(DataIntegrityViolationException error) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "El código ya existe o el registro tiene relaciones que impiden la operación");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validacion(MethodArgumentNotValidException error) {
        var problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Revisa los campos del registro");
        var campos = new LinkedHashMap<String, String>();
        error.getBindingResult().getFieldErrors().forEach(e -> campos.put(e.getField(), e.getDefaultMessage()));
        problema.setProperty("campos", campos);
        return problema;
    }
}
