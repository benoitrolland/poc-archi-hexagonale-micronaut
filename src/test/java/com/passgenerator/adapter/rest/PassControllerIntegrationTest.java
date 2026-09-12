package com.passgenerator.adapter.rest;

import com.passgenerator.domain.Pass;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest
class PassControllerIntegrationTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void testGeneratePassesEndpoint() {
        HttpResponse<String> response = client.toBlocking().exchange(
                HttpRequest.POST("/api/passes/generate?count=5", null), String.class);
        assertEquals(HttpStatus.ACCEPTED, response.getStatus());
        assertNotNull(response.body());
        assertFalse(response.body().isEmpty());
    }

    @Test
    void testGetBatchPassesEndpoint() throws InterruptedException {
        // Generate
        HttpResponse<String> genResponse = client.toBlocking().exchange(
                HttpRequest.POST("/api/passes/generate?count=3", null), String.class);
        String batchId = genResponse.body();

        // Wait for completion
        double progress;
        do {
            Thread.sleep(1000);
            HttpResponse<Double> progressResponse = client.toBlocking().exchange(
                    HttpRequest.GET("/api/passes/batch/" + batchId + "/progress"), Double.class);
            progress = progressResponse.body();
        } while (progress < 100.0);

        // Verify
        HttpResponse<List<Pass>> passesResponse = client.toBlocking().exchange(
			HttpRequest.GET("/api/passes/batch/" + batchId),
			Argument.listOf(Pass.class));

        assertEquals(HttpStatus.OK, passesResponse.getStatus());
        assertEquals(3, passesResponse.body().size());
    }
}