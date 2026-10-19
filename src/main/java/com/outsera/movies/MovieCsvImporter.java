package com.outsera.movies;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Converte as linhas do CSV em filmes. Colunas localizadas pelo nome do cabeçalho, em qualquer ordem:
 * {@code year}, {@code title} e {@code producers} são obrigatórias; {@code winner} e {@code studios}, opcionais.
 */
final class MovieCsvImporter {

    /** Um filme; o id vem da ordem das linhas de dados do CSV (a partir de 1). */
    record Movie(long id, int year, String title, String studios, boolean winner, List<String> producers) {
    }

    private static final List<String> REQUIRED = List.of("year", "title", "producers");

    /** "," (com ou sem espaço), ", and" e " and ". */
    private static final Pattern PRODUCER_SEPARATOR = Pattern.compile("\\s*,\\s*(?:and\\s+)?|\\s+and\\s+");

    private static final Pattern DIGITS = Pattern.compile("\\d{1,9}");

    private MovieCsvImporter() {
    }

    static List<Movie> toMovies(List<MovieCsvReader.Row> rows) {
        if (rows.isEmpty()) {
            throw new InvalidCsvException("arquivo CSV vazio: cabeçalho ausente");
        }
        List<String> header = rows.get(0).fields().stream().map(h -> h.trim().toLowerCase(Locale.ROOT)).toList();
        for (String column : header) {
            if (header.indexOf(column) != header.lastIndexOf(column)) {
                throw new InvalidCsvException("coluna duplicada no cabeçalho: " + column);
            }
        }
        List<String> missing = REQUIRED.stream().filter(column -> !header.contains(column)).toList();
        if (!missing.isEmpty()) {
            throw new InvalidCsvException("colunas obrigatórias ausentes: %s (cabeçalho encontrado: %s)"
                    .formatted(missing, header));
        }
        int year = header.indexOf("year");
        int title = header.indexOf("title");
        int producers = header.indexOf("producers");
        int studios = header.indexOf("studios");
        int winner = header.indexOf("winner");

        List<Movie> movies = new ArrayList<>(rows.size() - 1);
        for (MovieCsvReader.Row row : rows.subList(1, rows.size())) {
            List<String> f = row.fields();
            if (f.size() != header.size()) {
                throw new InvalidCsvException("linha %d: %d colunas, esperado %d: %s"
                        .formatted(row.line(), f.size(), header.size(), f));
            }
            String yearText = f.get(year).trim();
            if (!DIGITS.matcher(yearText).matches()) {
                throw new InvalidCsvException("linha %d: year inválido '%s': %s".formatted(row.line(), yearText, f));
            }
            List<String> names = splitProducers(f.get(producers));
            if (names.isEmpty()) {
                throw new InvalidCsvException("linha %d: producers vazio: %s".formatted(row.line(), f));
            }
            movies.add(new Movie(movies.size() + 1L, Integer.parseInt(yearText), f.get(title).trim(),
                    studios < 0 ? null : f.get(studios).trim(),
                    winner >= 0 && "yes".equalsIgnoreCase(f.get(winner).trim()), names));
        }
        return movies;
    }

    /** Separa, apara e remove nomes repetidos dentro do mesmo filme, preservando a ordem. */
    static List<String> splitProducers(String cell) {
        var names = new LinkedHashSet<String>();
        for (String name : PRODUCER_SEPARATOR.split(cell.trim())) {
            if (!name.isBlank()) {
                names.add(name.trim());
            }
        }
        return List.copyOf(names);
    }
}
