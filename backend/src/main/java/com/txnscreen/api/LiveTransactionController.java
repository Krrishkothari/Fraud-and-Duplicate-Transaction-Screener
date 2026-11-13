package com.txnscreen.api;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.txnscreen.service.LiveAppendService;
import com.txnscreen.service.RunService;

/**
 * Adds one live transaction to a run, so an analyst can see it screened and counted in its time window.
 */
@RestController
@RequestMapping("/api/runs/{id}/transactions")
public class LiveTransactionController {

    private final RunService runService;
    private final LiveAppendService liveAppendService;

    public LiveTransactionController(RunService runService, LiveAppendService liveAppendService) {
        this.runService = runService;
        this.liveAppendService = liveAppendService;
    }

    /**
     * Validates, screens and indexes one transaction. The threshold is not recomputed.
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public AppendResponse append(@PathVariable String id, @RequestBody AppendRequest request) {
        return AppendResponse.of(liveAppendService.append(runService.getRun(id), request.fields()));
    }
}
