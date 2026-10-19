package com.outsera.movies;

import java.util.ArrayList;
import java.util.List;

/**
 * Leitor CSV mínimo (RFC 4180): campos entre aspas, {@code ""} escapado, delimitador e quebra de linha dentro de
 * aspas, CRLF/LF e BOM. O delimitador ({@code ;} ou {@code ,}) é detectado pelo cabeçalho.
 */
final class MovieCsvReader {

    /** Um registro e a linha física (1-based) onde ele começa. */
    record Row(int line, List<String> fields) {
    }

    private static final List<String> KNOWN_COLUMNS = List.of("year", "title", "studios", "producers", "winner");

    private MovieCsvReader() {
    }

    static List<Row> read(String text) {
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }
        char delimiter = detectDelimiter(text);

        List<Row> rows = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean afterClosingQuote = false;
        boolean hasContent = false;
        int line = 1;
        int rowLine = 1;
        int quoteLine = 1;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                        afterClosingQuote = true;
                    }
                } else {
                    if (c == '\n') {
                        line++;
                    }
                    field.append(c);
                }
                continue;
            }
            if (c == delimiter) {
                fields.add(field.toString());
                field.setLength(0);
                afterClosingQuote = false;
                hasContent = true;
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                if (hasContent || field.length() > 0 || afterClosingQuote) {
                    fields.add(field.toString());
                    rows.add(new Row(rowLine, fields));
                    fields = new ArrayList<>();
                }
                field.setLength(0);
                afterClosingQuote = false;
                hasContent = false;
                line++;
                rowLine = line;
            } else if (afterClosingQuote) {
                throw new InvalidCsvException("linha %d: aspas inesperadas: caractere '%c' logo após o fechamento das aspas"
                        .formatted(line, c));
            } else if (c == '"' && field.isEmpty()) {
                inQuotes = true;
                hasContent = true;
                quoteLine = line;
            } else {
                field.append(c);
                hasContent = true;
            }
        }
        if (inQuotes) {
            throw new InvalidCsvException("linha %d: aspas não fechadas até o fim do arquivo".formatted(quoteLine));
        }
        if (hasContent || field.length() > 0 || afterClosingQuote) {
            fields.add(field.toString());
            rows.add(new Row(rowLine, fields));
        }
        return rows;
    }

    /** ';' por padrão; ',' só se o cabeçalho reconhecer mais colunas conhecidas com ele. */
    private static char detectDelimiter(String text) {
        int end = 0;
        while (end < text.length() && text.charAt(end) != '\n' && text.charAt(end) != '\r') {
            end++;
        }
        String header = text.substring(0, end);
        return knownColumns(header, ',') > knownColumns(header, ';') ? ',' : ';';
    }

    private static long knownColumns(String header, char delimiter) {
        return java.util.Arrays.stream(header.split(java.util.regex.Pattern.quote(String.valueOf(delimiter)), -1))
                .map(name -> name.replace("\"", "").trim().toLowerCase(java.util.Locale.ROOT))
                .filter(KNOWN_COLUMNS::contains)
                .count();
    }
}
