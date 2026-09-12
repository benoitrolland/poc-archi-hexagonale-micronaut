package com.passgenerator.adapter.cli;

import com.passgenerator.application.PassGenerationService;
import com.passgenerator.domain.Pass;
import jakarta.inject.Singleton;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import java.util.List;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

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
					case "5", "export" -> handleExport(parts);
					case "6", "exit", "quit" -> {
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
		// Cas 1 : argument positionnel explicite (ex: "3 <batchId>")
		if (parts.length >= 2 && !parts[1].isBlank()) {
			displayOneBatch(parts[1]);
			return;
		}

		// Cas 2 : pas d'argument → prompt interactif
		String answer = readLine("Batch ID (Entree pour tous les lots) : ").trim();

		if (answer.isEmpty()) {
			displayAllBatches();
		} else {
			displayOneBatch(answer);
		}
	}

	private void displayOneBatch(String batchId) {
		List<Pass> passes = passService.getGeneratedPasses(batchId);
		if (passes.isEmpty()) {
			out.println("Aucun Pass trouve pour le lot " + batchId);
			return;
		}
		out.printf("Lot %s - %d Pass :%n", batchId, passes.size());
		passes.forEach(p -> out.printf("  %s %s | vip=%s%n",
				p.getFirstName(), p.getLastName(), p.isVipStatus()));
	}

	private void displayAllBatches() {
		List<Pass> all = passService.findPassesByAttributes(null, null, null);
		if (all.isEmpty()) {
			out.println("Aucun Pass genere dans cette session.");
			return;
		}

		Map<String, List<Pass>> byBatch = all.stream()
				.filter(p -> p.getBatchId() != null)
				.collect(Collectors.groupingBy(
						Pass::getBatchId,
						LinkedHashMap::new,
						Collectors.toList()));

		byBatch.entrySet().stream()
				.sorted(Comparator.comparing(
						e -> e.getValue().get(0).getBatchCreatedAt(),
						Comparator.nullsLast(Comparator.reverseOrder())))
				.forEach(e -> {
					List<Pass> lotPasses = e.getValue();
					LocalDateTime created = lotPasses.get(0).getBatchCreatedAt();
					out.printf("Lot %s (cree le %s) - %d Pass :%n",
							e.getKey(),
							created != null ? created : "?",
							lotPasses.size());
					lotPasses.forEach(p -> out.printf("  %s %s | vip=%s%n",
							p.getFirstName(), p.getLastName(), p.isVipStatus()));
				});
	}

    private void handleSearch() throws Exception {
		String firstName = readLine("Prenom (optionnel) : ");
		String lastName  = readLine("Nom (optionnel) : ");
		String birthStr  = readLine("Date de naissance ISO (optionnel) : ");

		LocalDateTime bd = (birthStr == null || birthStr.isEmpty())
				? null : LocalDateTime.parse(birthStr);

		List<Pass> passes = passService.findPassesByAttributes(
				emptyToNull(firstName), emptyToNull(lastName), bd);

		if (passes.isEmpty()) {
			out.println("Aucun Pass trouve.");
			return;
		}

		// Groupe par lot, sans filtre : tous les Pass, tries par date de creation du lot
		Map<String, List<Pass>> byBatch = passes.stream()
				.filter(p -> p.getBatchId() != null)
				.collect(Collectors.groupingBy(
						Pass::getBatchId,
						LinkedHashMap::new,
						Collectors.toList()));

		byBatch.entrySet().stream()
				.sorted(Comparator.comparing(
						e -> e.getValue().get(0).getBatchCreatedAt(),
						Comparator.nullsLast(Comparator.reverseOrder())))
				.forEach(e -> {
					List<Pass> lotPasses = e.getValue();
					LocalDateTime created = lotPasses.get(0).getBatchCreatedAt();
					out.printf("Lot %s (cree le %s) - %d Pass :%n",
							e.getKey(),
							created != null ? created : "?",
							lotPasses.size());
					lotPasses.forEach(p -> out.printf("  %s %s | vip=%s%n",
							p.getFirstName(), p.getLastName(), p.isVipStatus()));
				});
	}

	private void handleExport(String[] parts) throws Exception {
		String batchId = resolveBatchId(parts);
		if (batchId == null) return;

		List<Pass> passes = passService.getGeneratedPasses(batchId);
		if (passes.isEmpty()) {
			out.println("Aucun Pass a exporter pour le lot " + batchId);
			return;
		}

		// Répertoire de destination
		String dirName = readLine("Repertoire de destination [./export-" + batchId + "] : ");
		java.nio.file.Path dir = java.nio.file.Paths.get(
				dirName.isEmpty() ? "export-" + batchId : dirName);
		java.nio.file.Files.createDirectories(dir);

		DateTimeFormatter fileFmt = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
		DateTimeFormatter idFmt   = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
		int exported = 0, skipped = 0;

		for (Pass p : passes) {
			if (p.getQrCode() == null || p.getQrCode().length == 0) {
				skipped++;
				continue;
			}
			// Nom de fichier : prenom-nom-yyyyMMddHHmmss.png
			String base = (p.getFirstName() + "-" + p.getLastName())
					.replaceAll("[^a-zA-Z0-9-]", "_");
			String timestamp = p.getGenerationDate() != null
					? p.getGenerationDate().format(idFmt)
					: "unknown";
			java.nio.file.Path file = dir.resolve(
					base + "-" + timestamp + "-" + exported + ".png");

			java.nio.file.Files.write(file, p.getQrCode());
			exported++;
		}

		out.printf("Export termine : %d QR code(s) ecrit(s) dans %s%n",
				exported, dir.toAbsolutePath());
		if (skipped > 0) {
			out.printf("  (%d Pass sans QR code ignore(s))%n", skipped);
		}
		java.nio.file.Path csv = dir.resolve("passes.csv");
		try (java.io.PrintWriter w = new java.io.PrintWriter(csv.toFile(), "UTF-8")) {
			w.println("firstName,lastName,birthDate,vipStatus,generationDate,batchId");
			for (Pass p : passes) {
				w.printf("%s,%s,%s,%s,%s,%s%n",
						p.getFirstName(),
						p.getLastName(),
						p.getBirthDate(),
						p.isVipStatus(),
						p.getGenerationDate(),
						p.getBatchId());
			}
		}
		out.println("Fichier CSV : " + csv.toAbsolutePath());
	}

    // ---------- Helpers ----------
	/** Utilitaire : transforme "" en null. */
	
	private static String emptyToNull(String s) {
		return (s == null || s.isEmpty()) ? null : s;
	}
	
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
		out.println("  1. Generer des Pass");
		out.println("  2. Verifier l'etat d'un lot");
		out.println("  3. Lister les Pass ou ceux d'un lot");
		out.println("  4. Rechercher par attributs");
		out.println("  5. Exporter les QR codes d'un lot");
		out.println("  6. Quitter");
		out.println("=====================================");
	}

}