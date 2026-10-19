package com.outsera.awards;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Produtores", description = "Intervalos entre prêmios de Pior Filme do Golden Raspberry Awards")
class AwardIntervalsController {

    private final AwardIntervalsCache cache;

    AwardIntervalsController(AwardIntervalsCache cache) {
        this.cache = cache;
    }

    @GetMapping(path = "/producers/award-intervals", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Menor e maior intervalo entre dois prêmios consecutivos de um mesmo produtor",
            description = "Empates aparecem todos. As listas vêm vazias se nenhum produtor tiver duas ou mais vitórias.")
    @ApiResponse(responseCode = "200", description = "Intervalos calculados a partir do CSV carregado no startup")
    AwardIntervals awardIntervals() {
        return cache.get();
    }
}
