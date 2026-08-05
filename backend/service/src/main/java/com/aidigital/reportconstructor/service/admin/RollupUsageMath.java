package com.aidigital.reportconstructor.service.admin;

import com.aidigital.reportconstructor.domain.reports.projections.UsageDailyBucket;
import com.aidigital.reportconstructor.domain.reports.projections.UsageDailyUserRow;
import com.aidigital.reportconstructor.service.reports.usage.ClaudeCostCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Reads the numbers out of a {@code usage_daily} row.
 *
 * <p>Two things are deliberately not stored in the rollup and are answered here instead. Cost is
 * computed from the row's token counts and the configured list prices for the model it names, so a
 * price change re-prices history rather than requiring a rebuild. And whether a row's jobs count as
 * reports is decided by {@link ReportCountPolicy} from the row's target and type, so the rule lives
 * in one Java class rather than being frozen into a stored column.
 *
 * <p>Every accessor tolerates a null aggregate. A {@code sum()} over an empty group cannot occur in
 * a {@code GROUP BY} result, but the same projection is built in tests and by hand, and a null there
 * should read as zero rather than throw.
 */
@Component
@RequiredArgsConstructor
public class RollupUsageMath {

	private final ClaudeCostCalculator costCalculator;
	private final ReportCountPolicy reportCountPolicy;

	/**
	 * Tells whether a rollup row's jobs count as standalone reports.
	 *
	 * @param bucket the rollup row
	 * @return true unless the row holds a slide-deck flow's intermediate sheet steps
	 */
	public boolean isCountable(UsageDailyBucket bucket) {
		return reportCountPolicy.isCountableReport(bucket.target(), bucket.reportTypeCode());
	}

	/**
	 * Tells whether a per-user rollup row's jobs count as standalone reports.
	 *
	 * @param row the per-user rollup row
	 * @return true unless the row holds a slide-deck flow's intermediate sheet steps
	 */
	public boolean isCountable(UsageDailyUserRow row) {
		return reportCountPolicy.isCountableReport(row.target(), row.reportTypeCode());
	}

	/**
	 * Every input-side token of a rollup row — plain input plus both prompt-cache classes.
	 *
	 * @param bucket the rollup row
	 * @return the total
	 */
	public long allInputTokens(UsageDailyBucket bucket) {
		return value(bucket.inputTokens()) + cacheTokens(bucket);
	}

	/**
	 * Prompt-cache write plus read tokens of a rollup row.
	 *
	 * @param bucket the rollup row
	 * @return the total
	 */
	public long cacheTokens(UsageDailyBucket bucket) {
		return value(bucket.cacheWriteTokens()) + value(bucket.cacheReadTokens());
	}

	/**
	 * Every token a rollup row accounts for.
	 *
	 * @param bucket the rollup row
	 * @return input, output and both cache classes, summed
	 */
	public long totalTokens(UsageDailyBucket bucket) {
		return allInputTokens(bucket) + value(bucket.outputTokens());
	}

	/**
	 * Prompt-cache write plus read tokens of a per-user rollup row.
	 *
	 * @param row the per-user rollup row
	 * @return the total
	 */
	public long cacheTokens(UsageDailyUserRow row) {
		return value(row.cacheWriteTokens()) + value(row.cacheReadTokens());
	}

	/**
	 * Every token a per-user rollup row accounts for.
	 *
	 * @param row the per-user rollup row
	 * @return input, output and both cache classes, summed
	 */
	public long totalTokens(UsageDailyUserRow row) {
		return value(row.inputTokens()) + value(row.outputTokens()) + cacheTokens(row);
	}

	/**
	 * Estimated cost of a rollup row at the configured list prices for the model it names.
	 *
	 * @param bucket the rollup row
	 * @return the cost in USD
	 */
	public double costUsd(UsageDailyBucket bucket) {
		return costCalculator.costUsd(
				value(bucket.inputTokens()), value(bucket.outputTokens()),
				value(bucket.cacheWriteTokens()), value(bucket.cacheReadTokens()), bucket.claudeModel());
	}

	/**
	 * Estimated cost of a per-user rollup row at the configured list prices for the model it names.
	 *
	 * @param row the per-user rollup row
	 * @return the cost in USD
	 */
	public double costUsd(UsageDailyUserRow row) {
		return costCalculator.costUsd(
				value(row.inputTokens()), value(row.outputTokens()),
				value(row.cacheWriteTokens()), value(row.cacheReadTokens()), row.claudeModel());
	}

	/**
	 * Reads a nullable aggregate as a number.
	 *
	 * @param aggregate the value from the projection
	 * @return the value, or 0 when absent
	 */
	public long value(Long aggregate) {
		return aggregate == null ? 0L : aggregate;
	}

	/**
	 * Percentage change between two periods, expressed as a signed percentage.
	 *
	 * <p>Null when the earlier period was zero: growth from nothing has no percentage, and reporting
	 * it as an infinite or 100% rise is the kind of number that makes a dashboard untrustworthy.
	 *
	 * @param current  the later period's value
	 * @param previous the earlier period's value
	 * @return the change in percent, or {@code null} when it is undefined
	 */
	public Double deltaPct(double current, double previous) {
		if (previous == 0d) {
			return null;
		}
		return (current - previous) / previous * 100d;
	}
}
