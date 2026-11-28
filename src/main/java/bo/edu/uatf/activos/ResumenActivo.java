package bo.edu.uatf.activos;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ResumenActivo(
        @Schema(example = "PROY-003", description = "Código único; en PUT se toma de la URL") String codigo,
        @Schema(example = "Proyector 3") String descripcion,
        @Schema(example = "3500") BigDecimal costoInicial,
        @Schema(example = "8", description = "Años de vida útil") BigDecimal vidaUtil,
        @Schema(description = "Fecha de incorporación; al crear se usa la fecha actual si se omite") LocalDate fechaIncorporacion,
        @Schema(example = "Bueno", allowableValues = {"Bueno", "Regular", "Malo"}) String estadoActivo,
        @Schema(example = "Luis Rojas", description = "Nombre del responsable; no es necesario escribir su ID") String responsable) {
    public static ResumenActivo de(Asignacion a) {
        return new ResumenActivo(a.codigo(), a.descripcion(), a.costoInicial(), a.vidaUtil(),
                a.fechaIncorporacion(), a.estadoActivo(), a.responsable());
    }
}
