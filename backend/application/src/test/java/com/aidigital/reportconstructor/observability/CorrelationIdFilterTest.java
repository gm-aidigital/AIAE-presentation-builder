package com.aidigital.reportconstructor.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CorrelationIdFilterTest {

	// doFilterInternal is protected; this test lives in the same package so it
	// can call it directly without a subclass or reflection.
	private final CorrelationIdFilter filter = new CorrelationIdFilter();

	@Test
	void shouldGenerateCorrelationIdWhenHeaderAbsentTest() throws Exception {
		// Given:
		HttpServletRequest request = mock(HttpServletRequest.class);
		HttpServletResponse response = mock(HttpServletResponse.class);
		FilterChain chain = mock(FilterChain.class);
		when(request.getHeader(CorrelationIdFilter.HEADER)).thenReturn(null);

		// When:
		filter.doFilterInternal(request, response, chain);

		// Then: a generated id is echoed, the chain runs, and MDC is cleared
		verify(response).setHeader(eq(CorrelationIdFilter.HEADER), anyString());
		verify(chain).doFilter(request, response);
		assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
	}

	@Test
	void shouldReuseIncomingCorrelationIdTest() throws Exception {
		// Given:
		HttpServletRequest request = mock(HttpServletRequest.class);
		HttpServletResponse response = mock(HttpServletResponse.class);
		FilterChain chain = mock(FilterChain.class);
		when(request.getHeader(CorrelationIdFilter.HEADER)).thenReturn("abc-123");

		// When:
		filter.doFilterInternal(request, response, chain);

		// Then: the incoming id is reused verbatim
		verify(response).setHeader(CorrelationIdFilter.HEADER, "abc-123");
		verify(chain).doFilter(request, response);
	}
}
