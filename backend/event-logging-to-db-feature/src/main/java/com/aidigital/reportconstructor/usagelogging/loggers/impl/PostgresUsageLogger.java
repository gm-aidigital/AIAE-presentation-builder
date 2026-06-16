// PostgresUsageLogger — dispatches UsageEvent persistence to a separate
// Spring bean. The actual INSERT lives in UsageEventPersistenceService so
// @Transactional(REQUIRES_NEW) and @Async are applied through a proxy.

package com.aidigital.reportconstructor.usagelogging.loggers.impl;

import com.aidigital.reportconstructor.usagelogging.loggers.UsageLogger;
import com.aidigital.reportconstructor.usagelogging.models.UsageEvent;
import com.aidigital.reportconstructor.usagelogging.sink.UsageEventSink;
import lombok.RequiredArgsConstructor;

/**
 * Dispatches usage events into the configured {@link UsageEventSink} chain.
 */
@RequiredArgsConstructor
public class PostgresUsageLogger implements UsageLogger {

	private final UsageEventSink usageEventSink;

	@Override
	public void record(UsageEvent event) {
		usageEventSink.record(event);
	}
}
