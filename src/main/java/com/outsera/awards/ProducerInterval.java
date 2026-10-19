package com.outsera.awards;

import io.swagger.v3.oas.annotations.media.Schema;

/** Intervalo entre duas vitórias adjacentes de um produtor. Os nomes dos campos são o contrato JSON da API. */
record ProducerInterval(
        @Schema(example = "Joel Silver") String producer,
        @Schema(description = "followingWin - previousWin", example = "1") int interval,
        @Schema(description = "Ano da primeira das duas vitórias", example = "1990") int previousWin,
        @Schema(description = "Ano da vitória seguinte", example = "1991") int followingWin) {
}
