package com.aidigital.reportconstructor.service.admin;

import com.aidigital.reportconstructor.service.admin.enums.AdminReportSort;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

class AdminReportSortResolverTest {

	private final AdminReportSortResolver resolver = new AdminReportSortResolver();

	@Test
	void shouldFallBackToTheDefaultColumnForAnUnknownCodeTest() {
		// When: a client asks for a column that does not exist — a stale bookmark, or an older build.
		AdminReportSort resolved = resolver.resolve("ownerFavouriteColour");

		// Then: it gets the default order rather than an error page.
		assertThat(resolved).isEqualTo(AdminReportSort.CREATED_AT);
	}

	@Test
	void shouldNotLetAClientNameAnArbitraryPropertyTest() {
		// When: a code that looks like a JPA path is sent.
		Pageable pageable = resolver.pageable(0, 20, "ownerEmail'; drop", "asc");

		// Then: the order names a vetted property, never the client's string — the value ends up in an
		// ORDER BY, so the enum is the boundary that keeps it from being one.
		assertThat(pageable.getSort()).containsExactly(
				Sort.Order.asc(AdminReportSort.CREATED_AT.getProperty()));
	}

	@Test
	void shouldClampThePageSizeTest() {
		// Then: an absent, zero or absurd size all land inside the allowed range — "one page" with an
		// unbounded size is the full-table read this whole change removed.
		assertThat(resolver.pageable(0, null, "tokens", "desc").getPageSize()).isEqualTo(50);
		assertThat(resolver.pageable(0, 0, "tokens", "desc").getPageSize()).isEqualTo(50);
		assertThat(resolver.pageable(0, 10_000, "tokens", "desc").getPageSize()).isEqualTo(200);
		assertThat(resolver.pageable(0, 25, "tokens", "desc").getPageSize()).isEqualTo(25);
	}

	@Test
	void shouldReadANegativePageAsTheFirstOneTest() {
		assertThat(resolver.pageable(-3, 20, "tokens", "desc").getPageNumber()).isZero();
		assertThat(resolver.pageable(null, 20, "tokens", "desc").getPageNumber()).isZero();
	}

	@Test
	void shouldAddAStableTiebreakToNonDefaultColumnsTest() {
		// When: ordering by a column full of ties — most reports ship a similar number of slides.
		Pageable pageable = resolver.pageable(0, 20, "slides", "desc");

		// Then: the id follows it, so paging through equal values cannot show one row twice and skip
		// another between requests.
		assertThat(pageable.getSort()).containsExactly(
				Sort.Order.desc("slideCount"), Sort.Order.desc("id"));
	}

	@Test
	void shouldDefaultToDescendingTest() {
		assertThat(resolver.direction(null)).isEqualTo(Sort.Direction.DESC);
		assertThat(resolver.direction("nonsense")).isEqualTo(Sort.Direction.DESC);
		assertThat(resolver.direction("ASC")).isEqualTo(Sort.Direction.ASC);
	}
}
