package com.aidigital.reportconstructor.externalservices.google;

import com.aidigital.reportconstructor.service.common.error.AppException;
import com.aidigital.reportconstructor.service.common.error.ErrorReason;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.googleapis.services.AbstractGoogleClientRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Executes a Google API request with capped exponential backoff on transient failures, shared by the
 * Sheets and Slides providers. Google marks HTTP 409 (ABORTED — concurrent-write conflict), 429 (rate
 * limit) and 500/503 (transient server errors) as retryable; large workbook copies and deck/sheet
 * {@code batchUpdate}s intermittently return these under load. Drive {@code files.copy} and Sheets/Slides
 * {@code batchUpdate} are each idempotent-enough for a retry: a copy that failed produced no file, and a
 * {@code batchUpdate} is atomic so a retried one never double-applies.
 */
@Slf4j
@Component
public class GoogleRequestRetrier {

	/**
	 * Attempts before giving up on a transient conflict/rate-limit/server error. Sized so the cumulative
	 * backoff (~31.5s at the {@link #MAX_BACKOFF_MILLIS} ceiling) comfortably outlasts Drive's post-copy
	 * propagation window on large templates — 5 attempts (~7.5s) proved too short and still surfaced 404s.
	 */
	private static final int MAX_ATTEMPTS = 8;

	/** Initial backoff before the first retry; doubled each attempt up to {@link #MAX_BACKOFF_MILLIS}. */
	private static final long INITIAL_BACKOFF_MILLIS = 500L;

	/** Ceiling for the exponential backoff between retries. */
	private static final long MAX_BACKOFF_MILLIS = 8_000L;

	/**
	 * Executes the request, retrying on transient statuses (409/429/500/503) with capped exponential
	 * backoff. Non-retryable errors and the final attempt's error propagate unchanged.
	 *
	 * @param request     the built Google client request to execute
	 * @param description short context used in retry log lines
	 * @param <T>         the request's response type
	 * @return the successful response
	 * @throws IOException when the request fails with a non-retryable error or exhausts all attempts
	 */
	public <T> T execute(AbstractGoogleClientRequest<T> request, String description) throws IOException {
		long backoff = INITIAL_BACKOFF_MILLIS;
		for (int attempt = 1; ; attempt++) {
			try {
				return request.execute();
			} catch (GoogleJsonResponseException ex) {
				if (!isRetryable(ex.getStatusCode()) || attempt >= MAX_ATTEMPTS) {
					throw ex;
				}
				log.warn("[google] {} attempt {}/{} failed with HTTP {} ({}) — retrying in {} ms",
						description, attempt, MAX_ATTEMPTS, ex.getStatusCode(), ex.getStatusMessage(), backoff);
				sleepBeforeRetry(backoff);
				backoff = Math.min(backoff * 2, MAX_BACKOFF_MILLIS);
			}
		}
	}

	/**
	 * Reports whether a Google API HTTP status is worth retrying: 404 (NOT_FOUND — a freshly copied
	 * workbook/deck not yet visible to the write replica), 409 (ABORTED — concurrent-write conflict),
	 * 429 (rate limit), and 500/503 (transient server errors).
	 *
	 * <p>404 is transient here because every {@code batchUpdate}/read this retrier wraps runs against a
	 * file we just created via Drive {@code files.copy}: the copy returns an id immediately, but Drive's
	 * write replica can lag the read replica by a few seconds (worse for large templates and shared-drive
	 * copies), so the first write to the new id intermittently returns NOT_FOUND before propagation settles.
	 * A genuinely bad id simply exhausts the attempts and propagates the same 404.
	 *
	 * @param statusCode the HTTP status returned by Google
	 * @return {@code true} when the request should be retried
	 */
	boolean isRetryable(int statusCode) {
		return statusCode == 404 || statusCode == 409 || statusCode == 429
				|| statusCode == 500 || statusCode == 503;
	}

	/**
	 * Sleeps for the given backoff between retries, restoring the interrupt flag and failing fast if the
	 * worker thread is interrupted while waiting.
	 *
	 * @param millis backoff duration in milliseconds
	 */
	void sleepBeforeRetry(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException ie) {
			Thread.currentThread().interrupt();
			throw new AppException(ErrorReason.C000, "Interrupted while retrying a Google API request");
		}
	}
}
