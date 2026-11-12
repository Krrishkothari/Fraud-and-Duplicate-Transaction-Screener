package com.txnscreen.api;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.txnscreen.service.AnalyticsService;
import com.txnscreen.service.Run;
import com.txnscreen.service.RunService;

/**
 * Answers time window questions about a run with its segment tree: the totals for one window, and a series
 * of consecutive windows for a chart. Window bounds are ISO-8601 timestamps with offset or epoch seconds.
 */
@RestController
@RequestMapping("/api/runs/{id}")
public class AnalyticsController {

    private final RunService runService;
    private final AnalyticsService analyticsService;

    public AnalyticsController(RunService runService, AnalyticsService analyticsService) {
        this.runService = runService;
        this.analyticsService = analyticsService;
    }

    /**
     * Returns the sum, count and maximum amount for one window.
     */
    @GetMapping("/window")
    public WindowResponse window(@PathVariable String id, @RequestParam String from, @RequestParam String to) {
        long fromEpoch = WindowTime.parse(from, "from");
        long toEpoch = WindowTime.parse(to, "to");
        Run run = runService.getRun(id);
        return WindowResponse.of(fromEpoch, toEpoch, analyticsService.window(run, fromEpoch, toEpoch));
    }

    /**
     * Splits a range into consecutive windows for a chart. Without from or to, the range starts or ends at
     * the run's data.
     */
    @GetMapping("/series")
    public SeriesResponse series(
            @PathVariable String id,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "" + AnalyticsService.DEFAULT_POINTS) int points) {
        if (points < AnalyticsService.MIN_POINTS || points > AnalyticsService.MAX_POINTS) {
            throw new ApiException("BAD_REQUEST", HttpStatus.BAD_REQUEST,
                    "Parameter 'points' must be from " + AnalyticsService.MIN_POINTS + " to "
                            + AnalyticsService.MAX_POINTS + ".",
                    Map.of("parameter", "points"));
        }
        Long fromEpoch = from == null ? null : WindowTime.parse(from, "from");
        Long toEpoch = to == null ? null : WindowTime.parse(to, "to");
        Run run = runService.getRun(id);
        return SeriesResponse.of(analyticsService.series(run, fromEpoch, toEpoch, points));
    }
}
