package com.passgenerator.domain;

import io.micronaut.serde.annotation.Serdeable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Serdeable
public class Pass {

    private String firstName;
    private String lastName;
    private LocalDateTime birthDate;
    private boolean vipStatus;
    private LocalDateTime requestDate;
    private LocalDateTime generationDate;
    private byte[] qrCode;
    private String passId;

    // NEW : traçabilité du lot d'origine
    private String batchId;
    private LocalDateTime batchCreatedAt;
}