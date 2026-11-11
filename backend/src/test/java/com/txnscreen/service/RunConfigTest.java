package com.txnscreen.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class RunConfigTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    // Proves null settings take the documented defaults, and the two optional ones stay null.
    @Test
    void nullsTakeDefaults() {
        RunConfig config = RunConfig.defaults();

        assertAll(
                () -> assertNull(config.expectedInsertions()),
                () -> assertEquals(0.01, config.targetFpr()),
                () -> assertNull(config.hashCount()),
                () -> assertEquals(60, config.bucketSeconds()),
                () -> assertEquals(99.0, config.percentile()),
                () -> assertEquals(42L, config.seed()));
    }

    // Proves the defaults pass validation.
    @Test
    void defaultsAreValid() {
        assertEquals(Set.of(), validator.validate(RunConfig.defaults()));
    }

    // Proves each out of range setting is reported once, by name.
    @Test
    void outOfRangeSettingsAreReported() {
        RunConfig config = new RunConfig(0, 1.0, 31, 0, 0.0, -1L);

        List<String> fields = validator.validate(config).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .sorted()
                .toList();

        assertEquals(List.of("bucketSeconds", "expectedInsertions", "hashCount", "percentile", "seed", "targetFpr"),
                fields);
    }

    // Proves the upper bounds: 10,000,000 expected insertions, 30 hashes, 86,400 second buckets and percentile
    // 100 are allowed, and one past each is not.
    @Test
    void upperBoundsAreInclusive() {
        RunConfig atLimit = new RunConfig(10_000_000, 0.999, 30, 86_400, 100.0, (long) Integer.MAX_VALUE);
        RunConfig pastLimit = new RunConfig(10_000_001, null, 31, 86_401, 100.5, null);

        List<String> fields = validator.validate(pastLimit).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .sorted()
                .toList();

        assertAll(
                () -> assertEquals(Set.of(), validator.validate(atLimit)),
                () -> assertEquals(List.of("bucketSeconds", "expectedInsertions", "hashCount", "percentile"), fields));
    }
}
