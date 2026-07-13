package com.aidigital.reportconstructor.externalservices.google;

import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.googleapis.services.AbstractGoogleClientRequest;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpResponseException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoogleRequestRetrierTest {

	@Test
	void shouldTreatConflictRateLimitAndServerErrorsAsRetryableTest() {
		// Given: a retrier
		GoogleRequestRetrier retrier = new GoogleRequestRetrier();

		// When-Then: only 409/429/500/503 are retryable
		assertThat(retrier.isRetryable(409)).isTrue();
		assertThat(retrier.isRetryable(429)).isTrue();
		assertThat(retrier.isRetryable(500)).isTrue();
		assertThat(retrier.isRetryable(503)).isTrue();
		assertThat(retrier.isRetryable(400)).isFalse();
		assertThat(retrier.isRetryable(403)).isFalse();
		assertThat(retrier.isRetryable(404)).isFalse();
	}

	@Test
	void shouldRetryTransientErrorThenReturnTest() throws IOException {
		// Given: a request that fails once with 500, then succeeds
		GoogleRequestRetrier retrier = spy(new GoogleRequestRetrier());
		doNothing().when(retrier).sleepBeforeRetry(anyLong());
		@SuppressWarnings("unchecked")
		AbstractGoogleClientRequest<String> request = mock(AbstractGoogleClientRequest.class);
		when(request.execute()).thenThrow(googleError(500)).thenReturn("ok");

		// When: executed through the retrier
		String result = retrier.execute(request, "copy");

		// Then: it retried once and returned the second attempt's value
		assertThat(result).isEqualTo("ok");
		verify(request, times(2)).execute();
		verify(retrier).sleepBeforeRetry(anyLong());
	}

	@Test
	void shouldPropagateNonRetryableErrorWithoutRetryTest() throws IOException {
		// Given: a request that fails with a non-retryable 404
		GoogleRequestRetrier retrier = spy(new GoogleRequestRetrier());
		@SuppressWarnings("unchecked")
		AbstractGoogleClientRequest<String> request = mock(AbstractGoogleClientRequest.class);
		GoogleJsonResponseException notFound = googleError(404);
		when(request.execute()).thenThrow(notFound);

		// When-Then: the error propagates immediately, no retry
		assertThatThrownBy(() -> retrier.execute(request, "copy")).isSameAs(notFound);
		verify(request, times(1)).execute();
		verify(retrier, times(0)).sleepBeforeRetry(anyLong());
	}

	@Test
	void shouldStopAfterFiveAttemptsAndRethrowTest() throws IOException {
		// Given: a request that always fails with 500
		GoogleRequestRetrier retrier = spy(new GoogleRequestRetrier());
		doNothing().when(retrier).sleepBeforeRetry(anyLong());
		@SuppressWarnings("unchecked")
		AbstractGoogleClientRequest<String> request = mock(AbstractGoogleClientRequest.class);
		when(request.execute()).thenThrow(googleError(500));

		// When-Then: it exhausts 5 attempts (4 backoff sleeps) then rethrows the last error
		assertThatThrownBy(() -> retrier.execute(request, "copy"))
				.isInstanceOf(GoogleJsonResponseException.class);
		verify(request, times(5)).execute();
		verify(retrier, times(4)).sleepBeforeRetry(anyLong());
	}

	private static GoogleJsonResponseException googleError(int code) {
		HttpResponseException.Builder builder = new HttpResponseException.Builder(code, "err", new HttpHeaders());
		GoogleJsonError details = new GoogleJsonError();
		details.setCode(code);
		return new GoogleJsonResponseException(builder, details);
	}
}
