package com.passgenerator.application.impl;

import com.passgenerator.application.PassGenerationService;
import com.passgenerator.domain.Pass;
import jakarta.inject.Singleton;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class PassGenerationServiceImpl implements PassGenerationService {

    private final Map<String, List<Pass>> batchPassesMap = new ConcurrentHashMap<>();
    private final Map<String, Double> batchProgressMap = new ConcurrentHashMap<>();
    private final Long genPassDuration;
    private final PassGeneratorAsyncProcessor asyncProcessor;

    public PassGenerationServiceImpl(PassGeneratorAsyncProcessor asyncProcessor) {
        this.asyncProcessor = asyncProcessor;
        this.genPassDuration = 1000L;
    }

    @Override
    public String submitBatchGeneration(int numberOfPasses) {
        String batchId = UUID.randomUUID().toString();
        LocalDateTime batchCreatedAt = LocalDateTime.now();  // NEW : horodatage du lot

        batchProgressMap.put(batchId, 0.0);
        batchPassesMap.put(batchId, new ArrayList<>());

        asyncProcessor.processPassesAsync(
                batchId,
                batchCreatedAt,           // NEW
                numberOfPasses,
                batchPassesMap,
                batchProgressMap,
                genPassDuration);

        return batchId;
    }

    @Override
    public List<Pass> getGeneratedPasses(String batchId) {
        return batchPassesMap.getOrDefault(batchId, Collections.emptyList());
    }

    @Override
    public List<Pass> findPassesByAttributes(String firstName, String lastName, LocalDateTime birthDate) {
        return batchPassesMap.values().stream()
                .flatMap(List::stream)
                .filter(pass -> (firstName == null || firstName.equals(pass.getFirstName()))
                        && (lastName == null || lastName.equals(pass.getLastName()))
                        && (birthDate == null || isSameDay(birthDate, pass.getBirthDate())))
                .toList();
    }

    @Override
    public Pass findPassByAttributes(String firstName, String lastName, LocalDateTime birthDate) {
        return findPassesByAttributes(firstName, lastName, birthDate)
                .stream().findFirst().orElse(null);
    }

    private boolean isSameDay(LocalDateTime date1, LocalDateTime date2) {
        return date1.toLocalDate().equals(date2.toLocalDate());
    }

    @Override
    public double getBatchProgress(String batchId) {
        return batchProgressMap.getOrDefault(batchId, 0.0);
    }
}