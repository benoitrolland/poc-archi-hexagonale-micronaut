package com.passgenerator.adapter.cli;

import com.passgenerator.application.PassGenerationService;
import com.passgenerator.domain.Pass;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Callable;

@Command(name = "pass-generator",
        description = "Pass Generator - Command Line Tool",
        mixinStandardHelpOptions = true,
        subcommands = {
                PassGeneratorPosixCli.GenerateCommand.class,
                PassGeneratorPosixCli.SearchCommand.class,
                PassGeneratorPosixCli.BatchCommand.class,
                PassGeneratorPosixCli.ExportCommand.class
        })
@Singleton
public class PassGeneratorPosixCli implements Callable<Integer> {

    @Inject
    PassGenerationService passService;

    @Override
    public Integer call() {
        System.out.println("Pass Generator CLI - Use --help to see available commands");
        return 0;
    }

    // ---------- generate ----------
    @Command(name = "generate",
            description = "Generate passes in a batch",
            mixinStandardHelpOptions = true)
    @Singleton
    static class GenerateCommand implements Callable<Integer> {

        @Inject
        PassGenerationService passService;

        @Parameters(index = "0", description = "Number of passes to generate")
        private int count;

        @Override
        public Integer call() {
            try {
                String batchId = passService.submitBatchGeneration(count);
                System.out.println("Batch generation started successfully!");
                System.out.println("Batch ID: " + batchId);
                return 0;
            } catch (Exception e) {
                System.err.println("Error generating passes: " + e.getMessage());
                return 1;
            }
        }
    }

    // ---------- search ----------
    @Command(name = "search",
            description = "Search for passes by attributes",
            mixinStandardHelpOptions = true)
    @Singleton
    static class SearchCommand implements Callable<Integer> {

        @Inject
        PassGenerationService passService;

        @Option(names = {"-f", "--first-name"}, description = "First name")
        private String firstName;

        @Option(names = {"-l", "--last-name"}, description = "Last name")
        private String lastName;

        @Option(names = {"-b", "--birth-date"},
                description = "Birth date (ISO format, e.g. 1990-01-01T00:00:00)")
        private String birthDate;

        @Override
        public Integer call() {
            LocalDateTime bd = birthDate != null ? LocalDateTime.parse(birthDate) : null;
            List<Pass> passes = passService.findPassesByAttributes(firstName, lastName, bd);
            if (passes.isEmpty()) {
                System.out.println("No passes found.");
                return 0;
            }
            passes.forEach(p -> System.out.printf(
                    "%s %s | birthDate=%s | vip=%s%n",
                    p.getFirstName(), p.getLastName(), p.getBirthDate(), p.isVipStatus()));
            return 0;
        }
    }


    // ---------- batch ----------
    @Command(name = "batch",
            description = "Manage a specific batch",
            mixinStandardHelpOptions = true)
    @Singleton
    static class BatchCommand implements Callable<Integer> {

        @Inject
        PassGenerationService passService;

        @Parameters(index = "0", description = "Batch ID")
        private String batchId;

        @Option(names = {"--progress"},
                description = "Show the batch progress only")
        private boolean showProgress;

        @Override
        public Integer call() {
            if (showProgress) {
                System.out.printf("Progress: %.2f%%%n", passService.getBatchProgress(batchId));
                return 0;
            }
            List<Pass> passes = passService.getGeneratedPasses(batchId);
            if (passes.isEmpty()) {
                System.out.println("Batch not found or empty: " + batchId);
                return 1;
            }
            System.out.printf("Batch %s — %d pass(es)%n", batchId, passes.size());
            passes.forEach(p -> System.out.printf(
                    "  %s %s | vip=%s%n",
                    p.getFirstName(), p.getLastName(), p.isVipStatus()));
            return 0;
        }
    }

    // ---------- export ----------
	@Command(name = "export", description = "Export QR codes of a batch to PNG files",
			mixinStandardHelpOptions = true)
	@Singleton
	static class ExportCommand implements Callable<Integer> {

		@Inject
		PassGenerationService passService;

		@Parameters(index = "0", description = "Batch ID")
		String batchId;

		@Parameters(index = "1", arity = "0..1",
				description = "Output directory (default: export-<batchId>)")
		String outputDir;

		@Override
		public Integer call() {
			List<Pass> passes = passService.getGeneratedPasses(batchId);
			if (passes.isEmpty()) {
				System.err.println("Aucun Pass pour le lot " + batchId);
				return 1;
			}

			java.nio.file.Path dir = java.nio.file.Paths.get(
					outputDir != null ? outputDir : "export-" + batchId);
			try {
				java.nio.file.Files.createDirectories(dir);
				int n = 0;
				for (Pass p : passes) {
					if (p.getQrCode() == null) continue;
					String base = (p.getFirstName() + "-" + p.getLastName())
							.replaceAll("[^a-zA-Z0-9-]", "_");
					java.nio.file.Path f = dir.resolve(base + "-" + n++ + ".png");
					java.nio.file.Files.write(f, p.getQrCode());
				}
				System.out.printf("Export : %d QR code(s) dans %s%n",
						n, dir.toAbsolutePath());
				return 0;
			} catch (Exception e) {
				System.err.println("Echec export : " + e.getMessage());
				return 1;
			}
		}
	}

}