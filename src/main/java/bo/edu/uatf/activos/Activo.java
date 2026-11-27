package bo.edu.uatf.activos;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record Activo(
        @NotBlank @Pattern(regexp = "[A-Z0-9][A-Z0-9._-]{0,14}") @Schema(example = "PROY-003") String codigo,
        @NotBlank @Size(max = 150) @Schema(example = "Proyector de laboratorio") String descripcion,
        @NotNull @Positive @Schema(example = "1") Integer oficinaId,
        @NotNull @Positive @Schema(example = "2") Integer responsableId,
        @Size(max = 15) @Schema(example = "") String codigoAdicional,
        @NotNull @Schema(example = "2026-11-26") LocalDate fechaIncorporacion,
        @NotBlank @Size(max = 50) @Schema(example = "MUEBLES Y ENSERES DE OFICINA") String grupo,
        @NotBlank @Size(max = 50) @Schema(example = "PROYECTOR") String auxiliar,
        @NotBlank @Pattern(regexp = "Bueno|Regular|Malo") @Schema(example = "Bueno") String estadoActivo,
        @NotBlank @Pattern(regexp = "ELABORADO") @Schema(example = "ELABORADO") String estadoRegistro,
        @Size(max = 4000) @Schema(example = "Registro ficticio para práctica SIS-414") String observaciones,
        @NotNull @DecimalMin("0") @Digits(integer = 13, fraction = 2) @Schema(example = "3500") BigDecimal costoInicial,
        @NotNull @DecimalMin("0") @Digits(integer = 13, fraction = 2) @Schema(example = "0") BigDecimal depreciacionInicial,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 4, fraction = 2) @Schema(example = "8") BigDecimal vidaUtil,
        @NotNull @Schema(example = "true") Boolean depreciar,
        @NotNull @Schema(example = "true") Boolean actualizar,
        @Size(max = 15) @Schema(example = "") String rube,
        @Size(max = 10) @Schema(example = "") String organismoFinanciador,
        @Size(max = 10) @Schema(example = "") String numeroConvenio,
        @NotBlank @Pattern(regexp = "0143") @Schema(example = "0143") String entidad,
        @NotBlank @Pattern(regexp = "1") @Schema(example = "1") String unidad) {
}
