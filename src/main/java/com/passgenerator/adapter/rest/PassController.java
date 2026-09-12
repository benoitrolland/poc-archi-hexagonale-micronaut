package com.passgenerator.adapter.rest;

import com.passgenerator.application.PassGenerationService;
import com.passgenerator.domain.Pass;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import jakarta.inject.Singleton;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Controller("/api/passes")
@Singleton
public class PassController {

    private final PassGenerationService passService;

    public PassController(PassGenerationService passService) {
        this.passService = passService;
    }

    @Post("/generate")
    public HttpResponse<String> generatePasses(@QueryValue int count) {
        String batchId = passService.submitBatchGeneration(count);
        return HttpResponse.accepted().body(batchId);
    }

    @Get("/batch/{batchId}")
    public HttpResponse<List<Pass>> getBatchPasses(@PathVariable String batchId) {
        return HttpResponse.ok(passService.getGeneratedPasses(batchId));
    }

    @Get("/batch/{batchId}/progress")
    public HttpResponse<Double> getBatchProgress(@PathVariable String batchId) {
        return HttpResponse.ok(passService.getBatchProgress(batchId));
    }

    @Get("/search")
    public HttpResponse<List<Pass>> findPasses(
            @QueryValue Optional<String> firstName,
            @QueryValue Optional<String> lastName,
            @QueryValue Optional<LocalDateTime> birthDate) {

        if (firstName.isEmpty() && lastName.isEmpty() && birthDate.isEmpty()) {
            return HttpResponse.badRequest();
        }

        List<Pass> passes = passService.findPassesByAttributes(
                firstName.orElse(null),
                lastName.orElse(null),
                birthDate.orElse(null));

        return passes.isEmpty() ? HttpResponse.notFound() : HttpResponse.ok(passes);
    }

    @Get("/search/single")
    public HttpResponse<Pass> findSinglePass(
            @QueryValue String firstName,
            @QueryValue String lastName,
            @QueryValue LocalDateTime birthDate) {
        Pass pass = passService.findPassByAttributes(firstName, lastName, birthDate);
        return pass != null ? HttpResponse.ok(pass) : HttpResponse.notFound();
    }
}