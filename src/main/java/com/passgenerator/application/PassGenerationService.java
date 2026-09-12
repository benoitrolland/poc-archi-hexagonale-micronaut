package com.passgenerator.application;

import com.passgenerator.domain.Pass;

import java.time.LocalDateTime;
import java.util.List;

public interface PassGenerationService {
    String submitBatchGeneration(int numberOfPasses);
    List<Pass> getGeneratedPasses(String batchId);
    Pass findPassByAttributes(String firstName, String lastName, LocalDateTime birthDate);
    List<Pass> findPassesByAttributes(String firstName, String lastName, LocalDateTime birthDate);
    double getBatchProgress(String batchId);
}