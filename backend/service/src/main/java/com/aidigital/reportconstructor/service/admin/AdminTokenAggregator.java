package com.aidigital.reportconstructor.service.admin;

import com.aidigital.reportconstructor.domain.reports.entities.ClaudeUsageEventEntity;
import com.aidigital.reportconstructor.service.admin.dto.AdminTokenDay;
import com.aidigital.reportconstructor.service.admin.dto.AdminTokenLabel;
import com.aidigital.reportconstructor.service.admin.dto.AdminTokenTotals;
import com.aidigital.reportconstructor.service.reports.enums.ClaudeUsageStatus;
import com.aidigital.reportconstructor.service.reports.usage.ClaudeCostCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Aggregates the per-call usage events into the dashboard's spend figures.
 *
 * <p>Measured and unknown calls are summed separately throughout. A call whose reply was lost to a
 * timeout was still billed, but its real cost cannot be known, so folding a guess into the headline
 * total would quietly turn a measured number into an estimate. Instead the guess is reported beside
 * the total — see {@link #predictedTokens}.
 */
@Component
@RequiredArgsConstructor
public class AdminTokenAggregator {

	/** Days covered by the token trend series. */
	private static final int WEEK_DAYS = 7;

	private final ClaudeCostCalculator costCalculator;

	/**
	 * Tells whether an event carries counts the API itself reported.
	 *
	 * @param event the usage event
	 * @return true when the event is measured rather than estimated
	 */
	public boolean isMeasured(ClaudeUsageEventEntity event) {
		return ClaudeUsageStatus.RECORDED.getCode().equals(event.getStatus());
	}

	/**
	 * Every token one event accounts for.
	 *
	 * @param event the usage event
	 * @return input + output + both cache classes
	 */
	public long totalTokens(ClaudeUsageEventEntity event) {
		return event.getInputTokens() + event.getOutputTokens()
				+ event.getCacheWriteTokens() + event.getCacheReadTokens();
	}

	/**
	 * Estimated cost of one event at the configured list prices for the model it billed against.
	 *
	 * @param event the usage event
	 * @return the cost in USD
	 */
	public double costUsd(ClaudeUsageEventEntity event) {
		return costCalculator.costUsd(
				event.getInputTokens(), event.getOutputTokens(),
				event.getCacheWriteTokens(), event.getCacheReadTokens(), event.getModel());
	}

	/**
	 * Sums the events into the token tab's headline figures.
	 *
	 * @param events every recorded usage event
	 * @param now    reference time for the "this month" window
	 * @return the aggregated token totals, all-zero when nothing has been recorded yet
	 */
	public AdminTokenTotals totals(List<ClaudeUsageEventEntity> events, OffsetDateTime now) {
		Map<String, Long> meanOutput = meanOutputByLabel(events);
		Set<Long> jobs = new HashSet<>();
		long calls = 0;
		long input = 0;
		long output = 0;
		long cacheWrite = 0;
		long cacheRead = 0;
		double cost = 0d;
		long tokensThisMonth = 0;
		double costThisMonth = 0d;
		long unknownCalls = 0;
		long estimatedTokens = 0;
		double estimatedCost = 0d;
		long unattributedCalls = 0;
		long unattributedTokens = 0;
		double unattributedCost = 0d;
		// Input-side tokens attributed to a report, kept apart from the unattributed ones so the
		// per-report averages below describe reports rather than all Claude traffic.
		long jobInput = 0;
		long jobOutput = 0;
		long jobTotal = 0;
		double jobCost = 0d;

		for (ClaudeUsageEventEntity event : events) {
			if (!isMeasured(event)) {
				unknownCalls++;
				long predicted = predictedTokens(event, meanOutput);
				estimatedTokens += predicted;
				estimatedCost += costCalculator.costUsd(
						event.getInputTokens(), predicted - event.getInputTokens(), 0, 0, event.getModel());
				continue;
			}
			calls++;
			input += event.getInputTokens();
			output += event.getOutputTokens();
			cacheWrite += event.getCacheWriteTokens();
			cacheRead += event.getCacheReadTokens();
			double eventCost = costUsd(event);
			cost += eventCost;
			if (isSameMonth(event.getCreatedAt(), now)) {
				tokensThisMonth += totalTokens(event);
				costThisMonth += eventCost;
			}
			if (event.getJobId() == null) {
				unattributedCalls++;
				unattributedTokens += totalTokens(event);
				unattributedCost += eventCost;
			} else {
				jobs.add(event.getJobId());
				jobInput += event.getInputTokens() + event.getCacheWriteTokens() + event.getCacheReadTokens();
				jobOutput += event.getOutputTokens();
				jobTotal += totalTokens(event);
				jobCost += eventCost;
			}
		}

		int reports = jobs.size();
		return new AdminTokenTotals(
				reports, calls, input, output, cacheWrite, cacheRead,
				input + output + cacheWrite + cacheRead, cost,
				tokensThisMonth, costThisMonth,
				perReport(jobTotal, reports),
				perReport(jobInput, reports),
				perReport(jobOutput, reports),
				reports == 0 ? 0d : jobCost / reports,
				unknownCalls, estimatedTokens, estimatedCost,
				unattributedCalls, unattributedTokens, unattributedCost);
	}

	/**
	 * Predicts what one lost call cost. Its prompt was measured locally before the request went out,
	 * so the input side is known within the estimator's accuracy; the output side is unknowable and is
	 * taken to be what comparable calls — the same batch tag — actually returned on average. When no
	 * comparable call has ever succeeded, the output side is left at zero rather than invented, which
	 * makes the prediction a floor rather than a fabrication.
	 *
	 * @param event      the estimated event
	 * @param meanOutput mean measured output tokens per batch tag
	 * @return predicted total tokens for the call
	 */
	long predictedTokens(ClaudeUsageEventEntity event, Map<String, Long> meanOutput) {
		return event.getInputTokens() + meanOutput.getOrDefault(event.getLabel(), 0L);
	}

	/**
	 * Mean measured output tokens per batch tag, used to predict the output side of lost calls.
	 *
	 * @param events every recorded usage event
	 * @return batch tag &rarr; mean output tokens of its measured calls
	 */
	Map<String, Long> meanOutputByLabel(List<ClaudeUsageEventEntity> events) {
		Map<String, Long> sums = new LinkedHashMap<>();
		Map<String, Long> counts = new LinkedHashMap<>();
		for (ClaudeUsageEventEntity event : events) {
			if (!isMeasured(event)) {
				continue;
			}
			sums.merge(event.getLabel(), event.getOutputTokens(), Long::sum);
			counts.merge(event.getLabel(), 1L, Long::sum);
		}
		Map<String, Long> means = new LinkedHashMap<>();
		for (Map.Entry<String, Long> entry : sums.entrySet()) {
			long count = counts.getOrDefault(entry.getKey(), 0L);
			means.put(entry.getKey(), count == 0 ? 0L : entry.getValue() / count);
		}
		return means;
	}

	/**
	 * Groups measured spend by the pipeline stage that caused it, most expensive first.
	 *
	 * @param events every recorded usage event
	 * @return per-stage spend rows
	 */
	public List<AdminTokenLabel> byLabel(List<ClaudeUsageEventEntity> events) {
		Map<String, long[]> counters = new LinkedHashMap<>();
		Map<String, Double> costs = new LinkedHashMap<>();
		for (ClaudeUsageEventEntity event : events) {
			// [0] measured calls, [1] measured tokens, [2] lost calls
			long[] row = counters.computeIfAbsent(event.getLabel(), k -> new long[3]);
			if (isMeasured(event)) {
				row[0]++;
				row[1] += totalTokens(event);
				costs.merge(event.getLabel(), costUsd(event), Double::sum);
			} else {
				row[2]++;
			}
		}
		List<AdminTokenLabel> rows = new ArrayList<>();
		for (Map.Entry<String, long[]> entry : counters.entrySet()) {
			long[] row = entry.getValue();
			rows.add(new AdminTokenLabel(
					entry.getKey(), row[0], row[1], costs.getOrDefault(entry.getKey(), 0d), row[2]));
		}
		rows.sort(Comparator.comparingLong(AdminTokenLabel::totalTokens).reversed());
		return rows;
	}

	/**
	 * Builds the last {@value #WEEK_DAYS} days of measured token spend, oldest first.
	 *
	 * @param events every recorded usage event
	 * @param now    reference time whose local date anchors "today"
	 * @return one spend point per day for the trailing week
	 */
	public List<AdminTokenDay> weekly(List<ClaudeUsageEventEntity> events, OffsetDateTime now) {
		LocalDate today = now.toLocalDate();
		Map<LocalDate, long[]> tokensByDay = new LinkedHashMap<>();
		Map<LocalDate, Double> costByDay = new LinkedHashMap<>();
		for (ClaudeUsageEventEntity event : events) {
			if (!isMeasured(event) || event.getCreatedAt() == null) {
				continue;
			}
			LocalDate day = event.getCreatedAt().toLocalDate();
			tokensByDay.computeIfAbsent(day, k -> new long[1])[0] += totalTokens(event);
			costByDay.merge(day, costUsd(event), Double::sum);
		}
		List<AdminTokenDay> series = new ArrayList<>();
		for (int i = WEEK_DAYS - 1; i >= 0; i--) {
			LocalDate day = today.minusDays(i);
			long[] tokens = tokensByDay.get(day);
			String label = day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
			series.add(new AdminTokenDay(
					day, label, tokens == null ? 0L : tokens[0], costByDay.getOrDefault(day, 0d)));
		}
		return series;
	}

	/**
	 * Divides a total by the number of reports it came from, guarding the empty case.
	 *
	 * @param total   the summed value
	 * @param reports reports the sum covers
	 * @return the mean, rounded down, or 0 when there are no reports
	 */
	long perReport(long total, int reports) {
		return reports == 0 ? 0L : total / reports;
	}

	/**
	 * Tells whether a timestamp falls in the same calendar month and year as the reference.
	 *
	 * @param when      timestamp under test, possibly {@code null}
	 * @param reference reference time
	 * @return true when {@code when} shares the reference year and month
	 */
	boolean isSameMonth(OffsetDateTime when, OffsetDateTime reference) {
		return when != null
				&& when.getYear() == reference.getYear()
				&& when.getMonth() == reference.getMonth();
	}
}
