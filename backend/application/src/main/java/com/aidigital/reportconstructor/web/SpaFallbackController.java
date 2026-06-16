// SpaFallbackController — serves index.html for client-side React Router paths
// when Spring Boot hosts the built SPA on port 5000 (Replit Deployment).
// Without this, deep links and browser refresh on non-root routes return 404.

package com.aidigital.reportconstructor.web;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Forwards unmatched SPA routes to the React entry point.
 */
@Controller
public class SpaFallbackController {

	private static final Resource INDEX = new ClassPathResource("static/index.html");

	/**
	 * Root and explicit index requests.
	 *
	 * @return the SPA shell when the static build exists
	 */
	@GetMapping(value = {"/", "/index.html"})
	public ResponseEntity<Resource> root() {
		return indexHtml();
	}

	/**
	 * Single-segment client routes (e.g. {@code /login}, {@code /reports}).
	 *
	 * @return the SPA shell when the path is not an API/static asset
	 */
	@GetMapping("/{path:^(?!api|actuator|swagger-ui|v3|assets|error|favicon\\.ico)[^\\.]+$}")
	public ResponseEntity<Resource> spaSingleSegment() {
		return indexHtml();
	}

	/**
	 * Nested client routes (e.g. {@code /reports/123/edit}).
	 *
	 * @return the SPA shell when the path is not an API/static asset
	 */
	@GetMapping("/{path:^(?!api|actuator|swagger-ui|v3|assets|error|favicon\\.ico)[^\\.]+}/**")
	public ResponseEntity<Resource> spaNested() {
		return indexHtml();
	}

	/**
	 * Returns the built SPA shell, or 404 when the frontend has not been packaged.
	 *
	 * @return HTML response for client-side routing, or not found
	 */
	ResponseEntity<Resource> indexHtml() {
		if (!INDEX.exists()) {
			return ResponseEntity.notFound().build();
		}
		return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(INDEX);
	}
}
