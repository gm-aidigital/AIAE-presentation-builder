// Serves OpenAPI spec at `/api/v1/specs/openapi.yaml` (read by SpringDoc +
// frontend `openapi-typescript`) while keeping the file OUT of
// `src/main/resources/static/` (Vite `emptyOutDir: true` would wipe it).
// Spec lives at `src/main/resources/api/v1/specs/openapi.yaml`; this
// WebMvcConfigurer maps the public URL → classpath. See
// `templates/generated-project/openapi/canonical-openapi-rules.md` → "Spec file".

package com.aidigital.reportconstructor.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves the committed OpenAPI contract from the canonical public URL.
 */
@Configuration
public class OpenApiSpecConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/api/v1/specs/**")
                .addResourceLocations("classpath:/api/v1/specs/");
    }
}
