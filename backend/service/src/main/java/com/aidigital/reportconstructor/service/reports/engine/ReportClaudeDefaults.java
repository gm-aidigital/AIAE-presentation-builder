package com.aidigital.reportconstructor.service.reports.engine;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.aidigital.reportconstructor.service.reports.dto.ClaudeResults;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeStrategic;
import com.aidigital.reportconstructor.service.reports.dto.ClaudeTactical;

/**
 * Empty Claude batch payloads used when a batch is skipped or the API returns nothing.
 */
@Component
public class ReportClaudeDefaults {

    /** Empty strategic batch (preview / skipped Batch A). */
    public ClaudeStrategic emptyStrategic() {
        return new ClaudeStrategic(null, null, null, List.of());
    }

    /** Empty tactical batch. */
    public ClaudeTactical emptyTactical() {
        return new ClaudeTactical(Map.of());
    }

    /** Empty results batch. */
    public ClaudeResults emptyResults() {
        return new ClaudeResults(null, List.of(), Map.of());
    }
}
