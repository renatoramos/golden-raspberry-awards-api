package com.outsera.movies;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.core.io.ClassPathResource;

/** Lê os valores esperados (TSV: produtor, tabulação, ano) gerados pelo oráculo Python. */
final class ScenarioFiles {

    private ScenarioFiles() {
    }

    static List<ProducerWin> expectedWins(String classpathTsv) {
        try (var in = new ClassPathResource(classpathTsv).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .filter(line -> !line.isBlank())
                    .map(line -> line.split("\t"))
                    .map(cols -> new ProducerWin(cols[0], Integer.parseInt(cols[1])))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
