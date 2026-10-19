package com.outsera.movies;

import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcProducerWins implements ProducerWins {

    private static final String DISTINCT_WINS = """
            select distinct mp.producer, m.release_year
              from movie_producer mp
              join movie m on m.id = mp.movie_id
             where m.winner
             order by mp.producer, m.release_year
            """;

    private final JdbcClient jdbc;

    JdbcProducerWins(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ProducerWin> distinctWins() {
        return jdbc.sql(DISTINCT_WINS)
                .query((rs, rowNum) -> new ProducerWin(rs.getString("producer"), rs.getInt("release_year")))
                .list();
    }
}
