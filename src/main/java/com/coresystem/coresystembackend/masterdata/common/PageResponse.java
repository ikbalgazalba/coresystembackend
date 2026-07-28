package com.coresystem.coresystembackend.masterdata.common;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * Standardized pagination envelope for master-data list endpoints (BR-BE07-20).
 *
 * <p>Every list/list-search endpoint in the master-data module returns this shape:
 * <pre>
 * {
 *   "items": [ ... ],          // page slice
 *   "page": 1,                 // requested page (0-based, matching Spring Data Pageable)
 *   "total_pages": 4,          // total page count
 *   "record_count": 10         // total record count (matches snake_case BR-BE07-20 field)
 * }
 * </pre>
 *
 * <p>The JSON field names are {@code total_pages} / {@code record_count} (snake_case) per
 * BR-BE07-20; the Java record component names are camelCase per project convention and are
 * serialized to snake_case by the configured Jackson naming strategy (or a later unit's DTO
 * mapper if the app does not set a global {@code PropertyNamingStrategy}). The record is a
 * carrier — it carries no behavior beyond the {@link #of(Page, int, int)} factory.
 *
 * @param items the page slice
 * @param page the requested page number (0-based, matching the {@code Pageable} passed to the query)
 * @param totalPages total number of pages
 * @param recordCount total number of records across all pages
 */
public record PageResponse<T>(List<T> items, int page, int totalPages, long recordCount) {

	/**
	 * Build a {@link PageResponse} from a Spring Data {@link Page}.
	 *
	 * <p>The {@code requestedPage} and {@code requestedSize} parameters are taken explicitly
	 * (rather than read from {@code page.getPageable()}) so the response echoes exactly what the
	 * client asked for even if the underlying {@link Page} was produced from a coerced/defaulted
	 * {@code Pageable}. {@code totalPages} is derived from {@link Page#getTotalPages()} and
	 * {@code recordCount} from {@link Page#getTotalElements()}.
	 *
	 * @param page the Spring Data {@link Page} to project
	 * @param requestedPage the 0-based page number the client requested (echoed in the response)
	 * @param requestedSize the page size the client requested (drives {@code totalPages} sanity)
	 * @param <T> the item type
	 * @return a {@link PageResponse} matching the BR-BE07-20 shape
	 */
	public static <T> PageResponse<T> of(Page<T> page, int requestedPage, int requestedSize) {
		return new PageResponse<>(
				page.getContent(),
				requestedPage,
				page.getTotalPages(),
				page.getTotalElements());
	}

}
// SDD-PROVENANCE: U-001 | vault: .mega-sdd/vaults/acquisition-master-data | PageResponse<T> record + static of(Page) — standardized pagination envelope {items[], page, total_pages, record_count} per BR-BE07-20
