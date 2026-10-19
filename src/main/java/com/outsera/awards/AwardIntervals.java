package com.outsera.awards;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** Resposta da API: menores (min) e maiores (max) intervalos entre vitórias consecutivas de um mesmo produtor. */
record AwardIntervals(
        @Schema(description = "Produtores com o menor intervalo") List<ProducerInterval> min,
        @Schema(description = "Produtores com o maior intervalo") List<ProducerInterval> max) {
}
