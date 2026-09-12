package com.passgenerator;

import com.passgenerator.adapter.cli.PassGeneratorInteractiveCli;
import com.passgenerator.adapter.cli.PassGeneratorPosixCli;
import io.micronaut.configuration.picocli.MicronautFactory;
import io.micronaut.context.ApplicationContext;
import io.micronaut.context.env.Environment;
import io.micronaut.runtime.Micronaut;
import org.yaml.snakeyaml.Yaml;
import picocli.CommandLine;

import java.io.InputStream;
import java.util.Map;
import java.util.Optional;

public class PassGeneratorApplication {

    private static final String PROPERTY = "app.cli.enabled";

    public static void main(String[] args) {
        if (isCliEnabled()) {
            runCli(args);
        } else {
            Micronaut.run(PassGeneratorApplication.class, args);
        }
    }

    private static boolean isCliEnabled() {
        // 1. System property (priorité max)
        String sys = System.getProperty(PROPERTY);
        if (sys != null) {
            System.err.println("[config] " + PROPERTY + " (sysprop) = " + sys);
            return Boolean.parseBoolean(sys);
        }

        // 2. application-cli.yml puis application.yml
        for (String name : new String[]{"application-cli.yml", "application.yml"}) {
            Optional<String> value = readYamlProperty(name, PROPERTY);
            if (value.isPresent()) {
                System.err.println("[config] " + PROPERTY + " (" + name + ") = " + value.get());
                return Boolean.parseBoolean(value.get());
            }
        }

        System.err.println("[config] " + PROPERTY + " introuvable, défaut = false (web)");
        return false;
    }

    @SuppressWarnings("unchecked")
    private static Optional<String> readYamlProperty(String fileName, String dottedKey) {
        try (InputStream is = PassGeneratorApplication.class.getResourceAsStream("/" + fileName)) {
            if (is == null) {
                System.err.println("[config] introuvable sur le classpath : " + fileName);
                return Optional.empty();
            }

            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(is);
            if (root == null) {
                System.err.println("[config] YAML vide : " + fileName);
                return Optional.empty();
            }

            System.err.println("[config] chargé " + fileName + " = " + root);

            Object current = root;
            for (String part : dottedKey.split("\\.")) {
                if (!(current instanceof Map<?, ?> map)) return Optional.empty();
                current = ((Map<String, Object>) map).get(part);
                if (current == null) return Optional.empty();
            }
            return Optional.of(String.valueOf(current));

        } catch (Exception e) {
            System.err.println("[config] erreur sur " + fileName + " : " + e.getMessage());
            return Optional.empty();
        }
    }

    private static void runCli(String[] args) {
        try (ApplicationContext context = ApplicationContext
                .builder(PassGeneratorApplication.class, Environment.CLI)
                .start()) {

            if (args.length == 0) {
                PassGeneratorInteractiveCli interactive =
                        context.getBean(PassGeneratorInteractiveCli.class);
                System.exit(interactive.run());
            } else {
                try (MicronautFactory factory = new MicronautFactory(context)) {
                    PassGeneratorPosixCli cli = context.getBean(PassGeneratorPosixCli.class);
                    CommandLine commandLine = new CommandLine(cli, factory);
                    System.exit(commandLine.execute(args));
                }
            }
        }
    }
}