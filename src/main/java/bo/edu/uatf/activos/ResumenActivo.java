package bo.edu.uatf.activos;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ResumenActivo(
        @Schema(example = "PROY-002", description = "Código único; en PUT se toma de la URL. Columna CODIGO") String codigo,
        @Schema(example = "Proyector de aula 2", description = "Columna DESCRIP") String descripcion,
        @Schema(example = "3500") BigDecimal costoInicial,
        @Schema(example = "8", description = "Años de vida útil") BigDecimal vidaUtil,
        @Schema(description = "Fecha de adquisición: DIA, MES y ANO en actual.DBF; Incorporación en VSIAF. Al crear se usa hoy si se omite", example = "2026-11-28") LocalDate fechaAdquisicion,
        @Schema(example = "Bueno", allowableValues = {"Bueno", "Regular", "Malo"}) String estadoActivo,
        @Schema(example = "Luis Rojas", description = "Nombre del responsable; no es necesario escribir su ID") String responsable) {
    public static ResumenActivo de(Asignacion a) {
        return new ResumenActivo(a.codigo(), a.descripcion(), a.costoInicial(), a.vidaUtil(),
                a.fechaIncorporacion(), a.estadoActivo(), a.responsable());
    }
}
