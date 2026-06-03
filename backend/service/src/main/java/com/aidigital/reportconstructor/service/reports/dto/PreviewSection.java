package com.aidigital.reportconstructor.service.reports.dto;

import java.util.List;

/** PreviewSection (report engine DTO). */
public record PreviewSection(String title, List<Placeholder> placeholders) {}
