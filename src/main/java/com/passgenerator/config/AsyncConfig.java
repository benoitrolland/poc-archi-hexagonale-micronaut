package com.passgenerator.config;

import io.micronaut.context.annotation.Factory;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Factory
public class AsyncConfig {

    @Singleton
    @Named("passGeneratorTaskExecutor")
    public ExecutorService taskExecutor() {
        return Executors.newFixedThreadPool(10);
    }
}