package com.passgenerator.adapter.cli;

import com.passgenerator.application.PassGenerationService;
import com.passgenerator.domain.Pass;
import jakarta.inject.Singleton;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * CLI interactive basée sur JLine, adaptée à Micronaut.
 * Le bean est un {@link Singleton} : le service est injecté par constructeur.
 */
@Singleton
public class PassGeneratorInteractiveCli {

    private final PassGenerationService passService;

    /** Dernier batch généré dans la session courante (stateful). */
    private String lastBatchId;

    public PassGeneratorInteractiveCli(PassGenerationService passService) {
        this.passService = passService;
    }

    public int run() {
        try (Terminal terminal = TerminalBuilder.builder().system(true).build()) {

            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .completer(new StringsCompleter(
                            "1", "2", "3", "4", "5",
                            "generate", "status", "list", "search", "help", "exit"))
                    .build();

            printBanner(terminal);

            while (true) {
                String line;
                try {
                    line = reader.readLine("pass-generator> ").trim();
                } catch (UserInterruptException | EndOfFileException e) {
                    break;
                }

                if (line.isEmpty()) continue;

                String[] parts = line.split("\\s+");
                String cmd = parts[0].toLowerCase();

                switch (cmd) {
                    case "1", "generate" -> handleGenerate(reader, terminal, parts);
                    case "2", "status"   -> handleStatus(reader, terminal, parts);
                    case "3", "list"     -> handleList(reader, terminal, parts);
                    case "4", "search"   -> handleSearch(reader, terminal);
                    case "5", "exit", "quit" -> {
                        terminal.writer().println("Bye!");
                        terminal.flush();
                        return 0;
                    }
                    case "help" -> printBanner(terminal);
                    default -> {
                        terminal.writer().println("Commande inconnue : " + cmd);
                        terminal.flush();
                    }
                }
            }
            return 0;
        } catch (IOException e) {
            System.err.println("Erreur terminal : " + e.getMessage());
            return 1;
        }
    }

    // ---------- Commandes ----------

    private void handleGenerate(LineReader reader, Terminal terminal, String[] parts) {
        int count = readInt(reader, terminal, parts, 1, "Nombre de Pass à générer : ");
        if (count <= 0) return;

        String batchId = passService.submitBatchGeneration(count);
        this.lastBatchId = batchId;

        terminal.writer().printf("Génération démarrée. Batch ID : %s%n", batchId);
        terminal.writer().println("(Ce lot sera utilisé par défaut pour les commandes 2 et 3)");
        terminal.flush();
    }

    private void handleStatus(LineReader reader, Terminal terminal, String[] parts) {
        String batchId = resolveBatchId(reader, terminal, parts);
        if (batchId == null) return;

        double progress = passService.getBatchProgress(batchId);
        terminal.writer().printf("Progression du lot %s : %.2f%%%n", batchId, progress);
        terminal.flush();
    }

    private void handleList(LineReader reader, Terminal terminal, String[] parts) {
        String batchId = resolveBatchId(reader, terminal, parts);
        if (batchId == null) return;

        List<Pass> passes = passService.getGeneratedPasses(batchId);
        if (passes.isEmpty()) {
            terminal.writer().println("Aucun Pass trouvé pour le lot " + batchId);
        } else {
            terminal.writer().printf("Lot %s — %d Pass :%n", batchId, passes.size());
            for (Pass p : passes) {
                terminal.writer().printf("  %s %s | vip=%s | généré=%s%n",
                        p.getFirstName(), p.getLastName(),
                        p.isVipStatus(), p.getGenerationDate());
            }
        }
        terminal.flush();
    }

    private void handleSearch(LineReader reader, Terminal terminal) {
        String firstName = prompt(reader, "Prénom (optionnel) : ");
        String lastName  = prompt(reader, "Nom (optionnel) : ");
        String birthStr  = prompt(reader, "Date de naissance ISO (optionnel) : ");

        LocalDateTime bd = birthStr.isEmpty() ? null : LocalDateTime.parse(birthStr);

        List<Pass> passes = passService.findPassesByAttributes(
                firstName.isEmpty() ? null : firstName,
                lastName.isEmpty() ? null : lastName,
                bd);

        if (passes.isEmpty()) {
            terminal.writer().println("Aucun Pass trouvé.");
        } else {
            passes.forEach(p -> terminal.writer().printf(
                    "  %s %s | vip=%s%n",
                    p.getFirstName(), p.getLastName(), p.isVipStatus()));
        }
        terminal.flush();
    }

    // ---------- Helpers ----------

    /**
     * Récupère le batch ID :
     *  1. depuis les arguments s'ils sont fournis (ex: "2 <id>")
     *  2. sinon, propose le dernier batchId connu comme valeur par défaut
     *  3. sinon, demande explicitement à l'utilisateur
     */
    private String resolveBatchId(LineReader reader, Terminal terminal, String[] parts) {
        // 1. Argument explicite
        if (parts.length >= 2 && !parts[1].isBlank()) {
            return parts[1];
        }

        // 2. Dernier batch connu → proposé par défaut
        if (lastBatchId != null) {
            String answer = prompt(reader,
                    "Batch ID [" + lastBatchId + "] (Entrée pour utiliser, sinon saisir un autre) : ");
            return answer.isEmpty() ? lastBatchId : answer;
        }

        // 3. Aucun batch connu → demande explicite
        String answer = prompt(reader, "Batch ID (aucun lot généré dans cette session) : ");
        if (answer.isEmpty()) {
            terminal.writer().println("Batch ID obligatoire pour cette commande.");
            terminal.flush();
            return null;
        }
        return answer;
    }

    private int readInt(LineReader reader, Terminal terminal, String[] parts,
                        int index, String prompt) {
        if (parts.length > index) {
            try {
                return Integer.parseInt(parts[index]);
            } catch (NumberFormatException e) {
                terminal.writer().println("Nombre invalide : " + parts[index]);
                terminal.flush();
                return -1;
            }
        }
        try {
            return Integer.parseInt(prompt(reader, prompt).trim());
        } catch (NumberFormatException e) {
            terminal.writer().println("Nombre invalide.");
            terminal.flush();
            return -1;
        }
    }

    private String prompt(LineReader reader, String message) {
        return reader.readLine(message).trim();
    }

    private void printBanner(Terminal terminal) {
        terminal.writer().println("=====================================");
        terminal.writer().println("  Pass Generator - CLI interactive");
        terminal.writer().println("=====================================");
        terminal.writer().println("  1. Générer des Pass");
        terminal.writer().println("  2. Vérifier l'état d'un lot");
        terminal.writer().println("  3. Lister les Pass d'un lot");
        terminal.writer().println("  4. Rechercher par attributs");
        terminal.writer().println("  5. Quitter");
        terminal.writer().println("=====================================");
        terminal.flush();
    }
}