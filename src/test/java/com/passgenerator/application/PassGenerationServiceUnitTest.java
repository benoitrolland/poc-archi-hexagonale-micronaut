package com.passgenerator.application;

import com.passgenerator.application.impl.PassGenerationServiceImpl;
import com.passgenerator.domain.Pass;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
class PassGenerationServiceUnitTest {

    @Inject
    PassGenerationServiceImpl passService;

    @Test
    void testSingleCallGeneration() throws Exception {
        String batchId = passService.submitBatchGeneration(5);
        assertNotNull(batchId);

        double progress;
        do {
            Thread.sleep(1000);
            progress = passService.getBatchProgress(batchId);
        } while (progress < 100.0);

        assertEquals(100.0, passService.getBatchProgress(batchId));
        List<Pass> passes = passService.getGeneratedPasses(batchId);
        assertEquals(5, passes.size());
    }
}