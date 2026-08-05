package com.aidigital.reportconstructor.service.admin;

import com.aidigital.reportconstructor.service.admin.enums.AdminReportSort;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Turns the paging and sorting a client asked for into a {@link Pageable} the database will accept.
 *
 * <p>Everything here is a clamp. An unknown sort code falls back to the default rather than
 * throwing — a stale bookmark or an older frontend asking for a column that no longer exists should
 * get the default order, not an error — and the page size is bounded at both ends, because "give me
 * one page" with an unbounded size is exactly the full-table read this whole change removed.
 */
@Component
public class AdminReportSortResolver {

	/** Rows returned when the client does not ask for a specific size. */
	private static final int DEFAULT_SIZE = 50;

	/** Most rows one page may carry, however large a size is requested. */
	private static final int MAX_SIZE = 200;

	/** Wire code for ascending order; anything else is read as descending. */
	private static final String ASCENDING = "asc";

	/**
	 * Builds the page request for a team-wide report listing.
	 *
	 * @param page zero-based page index, clamped at zero
	 * @param size rows per page, clamped to a sane range
	 * @param sort wire code of the column to order by
	 * @param dir  {@code asc} for ascending, anything else for descending
	 * @return the page request to hand to the repository
	 */
	public Pageable pageable(Integer page, Integer size, String sort, String dir) {
		AdminReportSort column = resolve(sort);
		Sort order = Sort.by(direction(dir), column.getProperty());
		// A secondary key on the surrogate id, so rows that tie on the chosen column keep a stable
		// order between requests. Without it, paging through equal values can show a row twice and
		// skip another.
		if (column != AdminReportSort.CREATED_AT) {
			order = order.and(Sort.by(Sort.Direction.DESC, "id"));
		}
		return PageRequest.of(Math.max(0, page == null ? 0 : page), clampSize(size), order);
	}

	/**
	 * Resolves a wire code to a sort column, falling back to the default.
	 *
	 * @param code the wire code, possibly {@code null}
	 * @return the matching column, or {@link AdminReportSort#CREATED_AT}
	 */
	public AdminReportSort resolve(String code) {
		if (code == null || code.isBlank()) {
			return AdminReportSort.CREATED_AT;
		}
		String wanted = code.trim().toLowerCase(Locale.ROOT);
		for (AdminReportSort candidate : AdminReportSort.values()) {
			if (candidate.getCode().toLowerCase(Locale.ROOT).equals(wanted)) {
				return candidate;
			}
		}
		return AdminReportSort.CREATED_AT;
	}

	/**
	 * Reads the requested direction.
	 *
	 * @param dir the wire code, possibly {@code null}
	 * @return ascending only when explicitly asked for, descending otherwise
	 */
	Sort.Direction direction(String dir) {
		return ASCENDING.equalsIgnoreCase(dir == null ? null : dir.trim())
				? Sort.Direction.ASC : Sort.Direction.DESC;
	}

	/**
	 * Clamps a requested page size into the allowed range.
	 *
	 * @param size the requested size, possibly {@code null}
	 * @return a size between 1 and {@value #MAX_SIZE}
	 */
	int clampSize(Integer size) {
		if (size == null || size <= 0) {
			return DEFAULT_SIZE;
		}
		return Math.min(size, MAX_SIZE);
	}
}
