package com.aidigital.reportconstructor.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Sets a request-scoped correlation id on every inbound HTTP request. The id
 * is taken from the {@code X-Correlation-Id} header when present, generated
 * (UUID v4) otherwise; written to SLF4J {@code MDC} so every log line emitted
 * during the request — including Logbook's JSON request/response — carries
 * it as a field; echoed back as response header so downstream services and
 * client logs can join on the same id.
 * <p>
 * Runs first (HIGHEST_PRECEDENCE) so MDC is populated before any other
 * filter logs. Cleared in {@code finally} so the thread-local doesn't bleed
 * into the next request served by the same Tomcat thread.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

	/**
	 * Header name used both for the incoming id and the echoed response header.
	 */
	public static final String HEADER = "X-Correlation-Id";
	/**
	 * MDC key — referenced by {@code logback-spring.xml} JSON encoder.
	 */
	public static final String MDC_KEY = "correlationId";

	/**
	 * Reads or generates the correlation id, sets MDC + response header,
	 * delegates to the chain, and clears MDC in {@code finally}.
	 *
	 * @param request  inbound servlet request
	 * @param response outbound servlet response
	 * @param chain    filter chain to delegate to
	 * @throws ServletException propagated from downstream filters
	 * @throws IOException      propagated from downstream filters
	 */
	@Override
	protected void doFilterInternal(HttpServletRequest request,
	                                HttpServletResponse response,
	                                FilterChain chain)
			throws ServletException, IOException {
		String incoming = request.getHeader(HEADER);
		String id = (incoming == null || incoming.isBlank())
				? UUID.randomUUID().toString()
				: incoming;
		MDC.put(MDC_KEY, id);
		response.setHeader(HEADER, id);
		try {
			chain.doFilter(request, response);
		} finally {
			MDC.remove(MDC_KEY);
		}
	}
}
