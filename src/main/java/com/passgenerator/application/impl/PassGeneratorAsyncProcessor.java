package com.passgenerator.application.impl;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.passgenerator.domain.Pass;
import io.micronaut.scheduling.annotation.Async;
import jakarta.inject.Singleton;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static com.passgenerator.domain.PassGeneratorConstants.FIRST_NAMES;
import static com.passgenerator.domain.PassGeneratorConstants.LAST_NAMES;

@Singleton
public class PassGeneratorAsyncProcessor {

    private final Random random = new Random();

    @Async
    public void processPassesAsync(String batchId,
                                    LocalDateTime batchCreatedAt,   // NEW
                                    int numberOfPasses,
                                    Map<String, List<Pass>> batchPassesMap,
                                    Map<String, Double> batchProgressMap,
                                    Long genPassDuration) {

        batchProgressMap.put(batchId, 0.0);
        List<Pass> passes = new ArrayList<>();
        List<Pass> regularPasses = new ArrayList<>();
        batchPassesMap.put(batchId, passes);

        for (int i = 0; i < numberOfPasses; i++) {
            Pass pass;
            try {
                pass = generateRandomPass(batchId, batchCreatedAt, genPassDuration);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            if (pass.isVipStatus()) {
                generateQRCode(pass);
                passes.add(pass);
            } else {
                regularPasses.add(pass);
            }
            int percent = ((i + 1) * 100) / numberOfPasses;
            if (percent == 100) {
                regularPasses.forEach(this::generateQRCode);
                passes.addAll(regularPasses);
            }
            batchProgressMap.put(batchId, (double) percent);
        }
    }

    private Pass generateRandomPass(String batchId,
                                     LocalDateTime batchCreatedAt,
                                     Long genPassDuration) throws InterruptedException {
        Thread.sleep(genPassDuration);
        return Pass.builder()
                .firstName(FIRST_NAMES[random.nextInt(FIRST_NAMES.length)])
                .lastName(LAST_NAMES[random.nextInt(LAST_NAMES.length)])
                .birthDate(LocalDateTime.now().minusYears(20L + random.nextInt(40)))
                .vipStatus(random.nextDouble() < 0.2)
                .requestDate(LocalDateTime.now())
                .batchId(batchId)                     // NEW
                .batchCreatedAt(batchCreatedAt)       // NEW
                .build();
    }

    private void generateQRCode(Pass pass) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            String passData = String.format("%s,%s,%s,%s",
                    pass.getFirstName(), pass.getLastName(), pass.getBirthDate(),
                    pass.isVipStatus() ? "VIP" : "REGULAR");
            BitMatrix bitMatrix = qrCodeWriter.encode(passData, BarcodeFormat.QR_CODE, 200, 200);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);
            pass.setQrCode(outputStream.toByteArray());
            pass.setGenerationDate(LocalDateTime.now());
        } catch (WriterException | IOException e) {
            throw new RuntimeException("Failed to generate QR code", e);
        }
    }
}