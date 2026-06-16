package com.aidigital.reportconstructor.service.reports.helpers;

import com.aidigital.reportconstructor.service.reports.dto.PreviewSection;

import java.util.List;
import java.util.Map;

/**
 * Flattens resolved preview sections into the double-brace token map used by Slides generation.
 */
public interface PlaceholderValueFlattener {

	/**
	 * Converts section placeholders to a token map, substituting an em dash for empty values.
	 *
	 * @param sections resolved preview sections produced by {@link PlaceholderSectionBuilder}
	 * @return ordered map of {@code {{token}}} keys to replacement strings
	 */
	Map<String, String> buildFlatReplacements(List<PreviewSection> sections);
}
