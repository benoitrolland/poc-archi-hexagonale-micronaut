package com.passgenerator.adapter.cli;

import com.passgenerator.application.PassGenerationService;
import com.passgenerator.domain.Pass;
import jakarta.inject.Singleton;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.time.LocalDateTime;
import java.util.List;

@Singleton
public class PassGeneratorInteractiveCli {

    private final PassGenerationService passService;
    private final BufferedReader reader;
    private final PrintStream out;

    private String lastBatchId;

    public PassGeneratorInteractiveCli(PassGenerationService passService) {
        this.passService = passService;
        this.reader = new BufferedReader(new InputStreamReader(System.in));
        this.out = System.out;
    }

    public int run() {
        try {
            printBanner();
            while (true) {
                String line = readLine("pass-generator> ");
                if (line == null) {           // Ctrl+D / fin de flux
                    out.println();
                    break;
                }
                line = line.trim();
                if (line.isEmpty()) continue;

                String[] parts = line.split("\\s+");
                String cmd = parts[0].toLowerCase();

                switch (cmd) {
                    case "1", "generate" -> handleGenerate(parts);
                    case "2", "status"   -> handleStatus(parts);
                    case "3", "list"     -> handleList(parts);
                    case "4", "search"   -> handleSearch();
                    case "5", "exit", "quit" -> {
                        out.println("Bye!");
                        return 0;
                    }
                    case "help" -> printBanner();
                    default -> out.println("Commande inconnue : " + cmd);
                }
            }
            return 0;
        } catch (Exception e) {
            System.err.println("Erreur : " + e.getMessage());
            return 1;
        }
    }

    // ---------- Commandes ----------

    private void handleGenerate(String[] parts) throws Exception {
        Integer count = readInt(parts, 1, "Nombre de Pass a generer : ");
        if (count == null || count <= 0) return;

        String batchId = passService.submitBatchGeneration(count);
        this.lastBatchId = batchId;

        out.printf("Generation demarree. Batch ID : %s%n", batchId);
        out.println("(Ce lot sera utilise par defaut pour les commandes 2 et 3)");
    }

    private void handleStatus(String[] parts) throws Exception {
        String batchId = resolveBatchId(parts);
        if (batchId == null) return;
        out.printf("Progression du lot %s : %.2f%%%n",
                batchId, passService.getBatchProgress(batchId));
    }

    private void handleList(String[] parts) throws Exception {
        String batchId = resolveBatchId(parts);
        if (batchId == null) return;

        List<Pass> passes = passService.getGeneratedPasses(batchId);
        if (passes.isEmpty()) {
            out.println("Aucun Pass trouve pour le lot " + batchId);
            return;
        }
        out.printf("Lot %s - %d Pass :%n", batchId, passes.size());
        for (Pass p : passes) {
            out.printf("  %s %s | vip=%s | genere=%s%n",
                    p.getFirstName(), p.getLastName(),
                    p.isVipStatus(), p.getGenerationDate());
        }
    }

    private void handleSearch() throws Exception {
        String firstName = readLine("Prenom (optionnel) : ");
        String lastName  = readLine("Nom (optionnel) : ");
        String birthStr  = readLine("Date de naissance ISO (optionnel) : ");

        LocalDateTime bd = (birthStr == null || birthStr.isEmpty())
                ? null : LocalDateTime.parse(birthStr);

        List<Pass> passes = passService.findPassesByAttributes(
                (firstName == null || firstName.isEmpty()) ? null : firstName,
                (lastName  == null || lastName.isEmpty())  ? null : lastName,
                bd);

        if (passes.isEmpty()) {
            out.println("Aucun Pass trouve.");
        } else {
            passes.forEach(p -> out.printf("  %s %s | vip=%s%n",
                    p.getFirstName(), p.getLastName(), p.isVipStatus()));
        }
    }

    // ---------- Helpers ----------

    /** Lit une ligne et l'affiche. Retourne null en fin de flux (Ctrl+D). */
    private String readLine(String prompt) throws Exception {
        out.print(prompt);
        out.flush();
        return reader.readLine();
    }

    private String resolveBatchId(String[] parts) throws Exception {
        // 1. Argument positionnel
        if (parts.length >= 2 && !parts[1].isBlank()) return parts[1];

        // 2. Dernier lot connu
        if (lastBatchId != null) {
            String answer = readLine("Batch ID [" + lastBatchId
                    + "] (Entree pour utiliser, sinon saisir un autre) : ").trim();
            return answer.isEmpty() ? lastBatchId : answer;
        }

        // 3. Aucun lot connu
        String answer = readLine("Batch ID (aucun lot genere dans cette session) : ").trim();
        if (answer.isEmpty()) {
            out.println("Batch ID obligatoire pour cette commande.");
            return null;
        }
        return answer;
    }

    private Integer readInt(String[] parts, int index, String prompt) throws Exception {
        if (parts.length > index) {
            try { return Integer.parseInt(parts[index]); }
            catch (NumberFormatException e) {
                out.println("Nombre invalide : " + parts[index]);
                return null;
            }
        }
        String s = readLine(prompt).trim();
        try { return Integer.parseInt(s); }
        catch (NumberFormatException e) {
            out.println("Nombre invalide.");
            return null;
        }
    }

    private void printBanner() {
        out.println("=====================================");
        out.println("  Pass Generator - CLI interactive");
        out.println("=====================================");
        out.println("  1. Génerer des Pass");
        out.println("  2. Verifier l'etat d'un lot");
        out.println("  3. Lister les Pass d'un lot");
        out.println("  4. Rechercher par attributs");
        out.println("  5. Quitter");
        out.println("=====================================");
    }
}