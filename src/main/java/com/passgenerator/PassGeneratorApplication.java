package com.passgenerator;

import com.passgenerator.adapter.cli.PassGeneratorPosixCli;
import io.micronaut.configuration.picocli.MicronautFactory;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.runtime.Micronaut;
import picocli.CommandLine;

public class PassGeneratorApplication {

    public static void main(String[] args) {
        boolean cliEnabled = Boolean.parseBoolean(
                System.getProperty("app.cli.enabled", "false"));

        if (cliEnabled) {
            runCli(args);
        } else {
            Micronaut.run(PassGeneratorApplication.class, args);
        }
    }

    private static void runCli(String[] args) {
        try (ApplicationContext context = ApplicationContext
                .builder(PassGeneratorApplication.class, Environment.CLI)
                .start();
             MicronautFactory factory = new MicronautFactory(context)) {

            PassGeneratorPosixCli cli = context.getBean(PassGeneratorPosixCli.class);

            // ⬇️ La factory est passée au CONSTRUCTEUR, pas via setFactory()
            CommandLine commandLine = new CommandLine(cli, factory);

            int exitCode = commandLine.execute(args);
            System.exit(exitCode);
        }
    }
}