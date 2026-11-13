package com.txnscreen.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.txnscreen.domain.Flag;
import com.txnscreen.domain.FlagType;
import com.txnscreen.domain.RejectedRow;
import com.txnscreen.domain.Transaction;
import com.txnscreen.ingest.BlacklistParser;
import com.txnscreen.ingest.CsvTransactionParser;
import com.txnscreen.ingest.ParseResult;
import com.txnscreen.service.Run;
import com.txnscreen.service.RunConfig;
import com.txnscreen.service.RunService;

import jakarta.validation.Valid;

/**
 * Creates screening runs from uploads or generated samples, and serves their summary, flags and rejected
 * rows.
 */
@RestController
@RequestMapping("/api/runs")
public class RunController {

    /** The largest page size for flags and rejected rows. */
    public static final int MAX_PAGE_SIZE = 200;

    private final CsvTransactionParser transactionParser;
    private final BlacklistParser blacklistParser;
    private final RunService runService;

    public RunController(CsvTransactionParser transactionParser, BlacklistParser blacklistParser,
            RunService runService) {
        this.transactionParser = transactionParser;
        this.blacklistParser = blacklistParser;
        this.runService = runService;
    }

    /**
     * Parses and screens an uploaded transactions CSV, with an optional blacklist CSV and JSON config part.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RunSummary> createRun(
            @RequestPart("transactions") MultipartFile transactions,
            @RequestPart(value = "blacklist", required = false) MultipartFile blacklist,
            @Valid @RequestPart(value = "config", required = false) RunConfig config) {
        ParseResult parsed = parse(transactions, transactionParser::parse);
        Set<String> blacklistIds = blacklist == null ? Set.of() : parse(blacklist, blacklistParser::parse);
        Run run = runService.createRun(parsed, blacklistIds, config == null ? RunConfig.defaults() : config);
        return created(run);
    }

    /**
     * Generates a synthetic dataset and screens it.
     */
    @PostMapping(path = "/sample", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RunSummary> createSampleRun(@Valid @RequestBody SampleRunRequest request) {
        Run run = runService.createSampleRun(request.size(), request.seed(), request.duplicateRate(),
                request.blacklistRate(), request.config());
        return created(run);
    }

    @GetMapping("/{id}")
    public RunSummary getRun(@PathVariable String id) {
        return RunSummary.of(runService.getRun(id));
    }

    /**
     * Returns one page of flags in screening order, optionally of one type only, each with its transaction.
     */
    @GetMapping("/{id}/flags")
    public PageResponse<FlaggedTransaction> getFlags(
            @PathVariable String id,
            @RequestParam(required = false) FlagType type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        requirePage(page, size);
        Run run = runService.getRun(id);
        // Flags are read before transactions because a live append adds its transaction before its flags.
        List<Flag> flags = run.screening().flags();
        List<Flag> matching = type == null ? flags : flags.stream().filter(flag -> flag.type() == type).toList();
        PageResponse<Flag> flagPage = PageResponse.of(matching, page, size);
        return flagPage.withItems(withTransactions(flagPage.items(), run.transactions()));
    }

    /**
     * Returns one page of rejected rows in file order.
     */
    @GetMapping("/{id}/rejected")
    public PageResponse<RejectedRow> getRejected(
            @PathVariable String id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        requirePage(page, size);
        return PageResponse.of(runService.getRun(id).rejected(), page, size);
    }

    private static List<FlaggedTransaction> withTransactions(List<Flag> flags, List<Transaction> transactions) {
        Set<Integer> lines = new HashSet<>();
        for (Flag flag : flags) {
            lines.add(flag.lineNumber());
        }
        Map<Integer, Transaction> byLine = new HashMap<>();
        for (Transaction transaction : transactions) {
            if (lines.contains(transaction.lineNumber())) {
                byLine.put(transaction.lineNumber(), transaction);
            }
        }
        return flags.stream().map(flag -> FlaggedTransaction.of(flag, byLine.get(flag.lineNumber()))).toList();
    }

    private static ResponseEntity<RunSummary> created(Run run) {
        return ResponseEntity.created(URI.create("/api/runs/" + run.id())).body(RunSummary.of(run));
    }

    private static void requirePage(int page, int size) {
        if (page < 0) {
            throw badParameter("Parameter 'page' must be 0 or more.", "page");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw badParameter("Parameter 'size' must be from 1 to " + MAX_PAGE_SIZE + ".", "size");
        }
    }

    private static ApiException badParameter(String message, String parameter) {
        return new ApiException("BAD_REQUEST", HttpStatus.BAD_REQUEST, message, Map.of("parameter", parameter));
    }

    private static <T> T parse(MultipartFile file, Function<InputStream, T> reader) {
        try (InputStream input = file.getInputStream()) {
            return reader.apply(input);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
