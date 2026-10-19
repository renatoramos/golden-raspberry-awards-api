package com.outsera.movies;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Carrega o CSV no banco quando os singletons terminam de ser criados, ou seja, antes de o servidor web abrir a
 * porta (um {@code ApplicationRunner} só rodaria depois). A leitura e a validação acontecem antes de qualquer
 * escrita, e a escrita é uma única transação: tudo ou nada.
 *
 * <p>Local do CSV: {@code app.movies.csv-location}, ou o alias {@code movielist.csv.location} (que prevalece se os
 * dois estiverem definidos); o padrão é {@code classpath:Movielist.csv}.
 */
@Component
class MovieLoader implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(MovieLoader.class);

    private static final String INSERT_MOVIE =
            "insert into movie (id, release_year, title, studios, winner) values (?, ?, ?, ?, ?)";
    private static final int[] MOVIE_TYPES = {Types.BIGINT, Types.INTEGER, Types.VARCHAR, Types.VARCHAR, Types.BOOLEAN};

    private static final String INSERT_PRODUCER = "insert into movie_producer (movie_id, producer) values (?, ?)";
    private static final int[] PRODUCER_TYPES = {Types.BIGINT, Types.VARCHAR};

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final Resource csv;

    MovieLoader(JdbcTemplate jdbc, TransactionTemplate transaction,
            @Value("${movielist.csv.location:${app.movies.csv-location:classpath:Movielist.csv}}") Resource csv) {
        this.jdbc = jdbc;
        this.transaction = transaction;
        this.csv = csv;
    }

    @Override
    public void afterSingletonsInstantiated() {
        List<MovieCsvImporter.Movie> movies = MovieCsvImporter.toMovies(MovieCsvReader.read(readCsv()));

        List<Object[]> movieArgs = new ArrayList<>(movies.size());
        List<Object[]> producerArgs = new ArrayList<>();
        for (MovieCsvImporter.Movie movie : movies) {
            movieArgs.add(new Object[] {movie.id(), movie.year(), movie.title(), movie.studios(), movie.winner()});
            movie.producers().forEach(producer -> producerArgs.add(new Object[] {movie.id(), producer}));
        }

        transaction.executeWithoutResult(status -> {
            jdbc.batchUpdate(INSERT_MOVIE, movieArgs, MOVIE_TYPES);
            jdbc.batchUpdate(INSERT_PRODUCER, producerArgs, PRODUCER_TYPES);
        });

        log.info("{} filmes carregados de {} ({} vencedores, {} vínculos produtor-filme)", movies.size(),
                csv.getDescription(), movies.stream().filter(MovieCsvImporter.Movie::winner).count(),
                producerArgs.size());
    }

    private String readCsv() {
        try {
            return csv.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("não foi possível ler o CSV " + csv.getDescription(), e);
        }
    }
}
