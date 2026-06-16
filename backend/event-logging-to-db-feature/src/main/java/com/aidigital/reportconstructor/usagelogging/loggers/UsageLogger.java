// UsageLogger — sink interface. Implementations: PostgresUsageLogger,
// NoOpUsageLogger (loggers/impl). Business code NEVER calls this directly —
// the @LogUsage annotation + UsageLoggingAspect own dispatch.
// See observability/usage-logging-rules.md.

package com.aidigital.reportconstructor.usagelogging.loggers;

import com.aidigital.reportconstructor.usagelogging.models.UsageEvent;

/**
 * Sink for usage logging events emitted by service methods.
 */
public interface UsageLogger {

	/**
	 * Persists a single usage event. Must NOT block / throw into the caller
	 * — implementations swallow infra errors and surface them via local
	 * warning logs.
	 *
	 * @param event fully populated event from UsageLoggingAspect
	 */
	void record(UsageEvent event);
}
