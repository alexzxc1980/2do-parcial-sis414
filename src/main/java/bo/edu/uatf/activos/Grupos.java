package bo.edu.uatf.activos;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.List;

@Component
public class Grupos {
    public record Grupo(int codigo, String nombre, boolean depreciar, boolean actualizar) { }
    private final List<Grupo> grupos;

    public Grupos(ObjectMapper json) throws IOException {
        try (var entrada = getClass().getResourceAsStream("/grupos.json")) {
            grupos = json.readValue(entrada, new TypeReference<List<Grupo>>() { });
        }
    }

    public List<Grupo> listar() { return grupos; }
}
