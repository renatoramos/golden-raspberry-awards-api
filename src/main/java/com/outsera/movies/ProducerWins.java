package com.outsera.movies;

import java.util.List;

/**
 * API pública do módulo {@code movies}: único ponto de acesso aos dados de vitórias.
 */
public interface ProducerWins {

    /**
     * Vitórias distintas (produtor, ano), ordenadas por produtor e, dentro dele, por ano.
     */
    List<ProducerWin> distinctWins();
}
