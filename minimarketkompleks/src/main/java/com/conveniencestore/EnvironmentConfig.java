package com.conveniencestore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.stream.Stream;

final class EnvironmentConfig {
    private EnvironmentConfig() {
    }

    static Properties load() {
        Path directory = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        while (directory != null) {
            Path envFile = directory.resolve(".env");
            if (Files.isRegularFile(envFile)) {
                try {
                    return parse(envFile);
                } catch (IOException e) {
                    throw new IllegalStateException("Unable to read " + envFile + ".", e);
                }
            }
            directory = directory.getParent();
        }
        return new Properties();
    }

    static Properties parse(Path path) throws IOException {
        Properties properties = new Properties();
        try (Stream<String> lines = Files.lines(path, StandardCharsets.UTF_8)) {
            lines.forEach(line -> parseLine(line, properties));
        }
        return properties;
    }

    static String get(Properties properties, String name) {
        String environmentValue = System.getenv(name);
        return environmentValue == null || environmentValue.trim().isEmpty()
                ? properties.getProperty(name) : environmentValue;
    }

    private static void parseLine(String line, Properties properties) {
        String entry = line.trim();
        if (entry.isEmpty() || entry.startsWith("#")) {
            return;
        }
        if (entry.startsWith("export ")) {
            entry = entry.substring("export ".length()).trim();
        }

        int separator = entry.indexOf('=');
        if (separator <= 0) {
            return;
        }
        String name = entry.substring(0, separator).trim();
        String value = entry.substring(separator + 1).trim();
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                value = value.substring(1, value.length() - 1);
            }
        }
        properties.setProperty(name, value);
    }
}
