package com.txnscreen.api;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.txnscreen.service.Run;
import com.txnscreen.service.RunService;
import com.txnscreen.service.ThresholdService;

/**
 * Finds a percentile of a run's amounts with randomized Quickselect, for the whole run or one time window.
 * This lets an analyst explore thresholds; the HIGH_AMOUNT flags of a run always use the threshold set when
 * the run was created.
 */
@RestController
@RequestMapping("/api/runs/{id}")
public class ThresholdController {

    private final RunService runService;
    private final ThresholdService thresholdService;

    public ThresholdController(RunService runService, ThresholdService thresholdService) {
        this.runService = runService;
        this.thresholdService = thresholdService;
    }

    /**
     * Returns the p-th percentile amount and the number of amounts above it. Without p, the run's configured
     * percentile is used. Without from or to, the window starts or ends at the run's data. Window bounds are
     * ISO-8601 timestamps with offset or epoch seconds.
     */
    @GetMapping("/threshold")
    public ThresholdResponse threshold(
            @PathVariable String id,
            @RequestParam(required = false) Double p,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        if (p != null && !(p > 0 && p <= 100)) {
            throw new ApiException("INVALID_CONFIG", HttpStatus.BAD_REQUEST, "The configuration is invalid.",
                    Map.of("fieldErrors", List.of(new FieldViolation("p", "must be greater than 0 and at most 100"))));
        }
        Long fromEpoch = from == null ? null : WindowTime.parse(from, "from");
        Long toEpoch = to == null ? null : WindowTime.parse(to, "to");
        Run run = runService.getRun(id);
        double percentile = p == null ? run.config().percentile() : p;
        return ThresholdResponse.of(thresholdService.forWindow(run, percentile, fromEpoch, toEpoch));
    }
}
