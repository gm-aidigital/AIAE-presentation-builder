package com.aidigital.reportconstructor.service.reports.dto;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A chart on a breakdown slide, i.e. one data series that gets its own source workbook. A
 * {@link BreakdownType} is not enough to name a chart any more: the audience slide carries two charts
 * (age distribution and top audience segments), each linked to its own workbook and fed by a different
 * sub-table of the sheet's audience block.
 *
 * <p>The {@link #code()} is the configuration key under {@code external.google.breakdown-charts.*}, so
 * every chart's source workbook and in-sheet chart id are configured per series rather than per section.
 * The audience age series keeps the bare {@code "aud"} code it had when it was the section's only chart,
 * so existing configuration binds unchanged.
 */
public enum BreakdownChartSeries {

	/** Age distribution on the audience slide: age bucket → delivered impressions. */
	AUDIENCE_AGE("aud", BreakdownType.AUDIENCE, false),

	/** Top audience segments on the audience slide: segment name → affinity index (100 = campaign average). */
	AUDIENCE_SEGMENT("aud-seg", BreakdownType.AUDIENCE, true),

	/** Device breakdown: device → delivered impressions. */
	DEVICE("dev", BreakdownType.DEVICE, false);

	/**
	 * Lookup from each series' configuration {@code code} to the series. A shared constant rather than a
	 * static factory method, which the structure rules forbid.
	 */
	public static final Map<String, BreakdownChartSeries> BY_CODE = Stream.of(values())
			.collect(Collectors.toUnmodifiableMap(BreakdownChartSeries::code, Function.identity()));

	private final String code;
	private final BreakdownType section;
	private final boolean labelsFromData;

	BreakdownChartSeries(String code, BreakdownType section, boolean labelsFromData) {
		this.code = code;
		this.section = section;
		this.labelsFromData = labelsFromData;
	}

	/**
	 * Returns the configuration key this series is keyed by.
	 *
	 * @return the series' wire/config code (e.g. {@code "aud-seg"})
	 */
	public String code() {
		return code;
	}

	/**
	 * Returns the breakdown section whose slide carries this chart — which is also what decides the slide
	 * the chart is looked for on.
	 *
	 * @return the owning breakdown section
	 */
	public BreakdownType section() {
		return section;
	}

	/**
	 * Whether this series' category labels come from the report rather than from the source workbook.
	 *
	 * <p>Age buckets and device names are a fixed list the workbook already prints in its category column,
	 * so those series only write values next to the labels they matched. Audience segment names differ per
	 * campaign, so nothing in the workbook could be matched — both label and value have to be written.
	 *
	 * @return {@code true} when labels must be written into the workbook, {@code false} when they are matched
	 */
	public boolean labelsFromData() {
		return labelsFromData;
	}
}
