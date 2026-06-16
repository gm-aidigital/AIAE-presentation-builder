package com.aidigital.reportconstructor.service.reports.services;

/**
 * A single label/value pair surfaced as a chip in the preview "all labels" panel.
 *
 * @param label the row's first-column label text (e.g. {@code "Client name:"})
 * @param value the row's second-column value text
 */
public record LabelChip(String label, String value) {

}
