package com.aidigital.reportconstructor.service.admin.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Typed configuration for the dashboard's "time and money saved" figure, bound from
 * {@code app.savings.*}.
 *
 * <p>Every number here is an assumption about work that did not happen, which is exactly why it is
 * configuration and not code: the baseline and the rate are the two inputs a reader will disagree
 * with, and they must be arguable without a deploy.
 *
 * <p>The model is deliberately simple. A slide built by hand — pulling the numbers, laying it out,
 * writing the copy — costs {@link #manualMinutesPerSlide}; a report's manual cost is that times the
 * slides it shipped; the saving is that cost minus the wall-clock time the generated run actually
 * took, valued at {@link #hourlyRateUsd}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.savings")
public class SavingsProperties {

	/** Loaded hourly rate the saved time is valued at, in USD. */
	private double hourlyRateUsd = 14d;

	/**
	 * Minutes one slide costs to build by hand, including gathering its data and writing its copy.
	 *
	 * <p>At the default, a 25-slide deck is 6h15m of manual work and a 16-slide deck is 4h.
	 */
	private int manualMinutesPerSlide = 15;

	/**
	 * Slides to assume for a report whose deck was never measured, keyed by report type code.
	 *
	 * <p>Applies to runs that predate slide counting and to runs whose deck could not be read. Without
	 * it those reports would count as zero slides — silently saving nothing — which understates the
	 * figure rather than leaving it uncertain.
	 */
	private Map<String, Integer> defaultSlidesByType = new LinkedHashMap<>(
			Map.of("EOC", 25, "EOM", 16));

	/** Slides to assume when the report type has no configured default either. */
	private int defaultSlides = 20;

	/**
	 * Whether the wall-clock time a generated run took is subtracted from the saving.
	 *
	 * <p>On by default: the automation is fast, not instant, and a figure that ignores its own cost is
	 * the kind of number that stops being believed.
	 */
	private boolean subtractGenerationTime = true;
}
