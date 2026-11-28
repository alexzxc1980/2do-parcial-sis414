package bo.edu.uatf.activos;

import jakarta.servlet.http.HttpServletRequest;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springdoc.webmvc.ui.SwaggerIndexPageTransformer;
import org.springdoc.webmvc.ui.SwaggerWelcomeCommon;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.resource.ResourceTransformerChain;
import org.springframework.web.servlet.resource.TransformedResource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class SwaggerEnVivo extends SwaggerIndexPageTransformer {
    public SwaggerEnVivo(SwaggerUiConfigProperties config, SwaggerUiOAuthProperties oauth,
            SwaggerWelcomeCommon welcome, ObjectMapperProvider mapper) {
        super(config, oauth, welcome, mapper);
    }

    @Override
    public Resource transform(HttpServletRequest request, Resource resource, ResourceTransformerChain chain) throws IOException {
        var original = super.transform(request, resource, chain);
        if (!"swagger-initializer.js".equals(resource.getFilename())) return original;
        try (var entrada = original.getInputStream(); var script = getClass().getResourceAsStream("/swagger-actualizacion.js")) {
            var contenido = new String(entrada.readAllBytes(), StandardCharsets.UTF_8)
                    .replace("SwaggerUIBundle.plugins.DownloadUrl", "SeguimientoActivos, CatalogosReales, AsignacionNuevoActivo, SwaggerUIBundle.plugins.DownloadUrl");
            var plugin = new String(script.readAllBytes(), StandardCharsets.UTF_8);
            return new TransformedResource(resource, (plugin + "\n" + contenido).getBytes(StandardCharsets.UTF_8));
        }
    }
}
