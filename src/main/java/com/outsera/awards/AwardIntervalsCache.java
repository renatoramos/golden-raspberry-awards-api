package com.outsera.awards;

import org.springframework.stereotype.Component;

import com.outsera.movies.ProducerWins;

/**
 * Guarda o resultado dos intervalos, calculado uma única vez. O aquecimento acontece antes de a porta abrir
 * ({@link AwardIntervalsWarmUp}); se mesmo assim uma chamada chegar primeiro, a dupla verificação sincronizada
 * garante um único cálculo.
 *
 * <p><b>Condição de validade:</b> os dados só mudam na carga do CSV, que termina antes de o servidor abrir a porta, e
 * nenhuma operação escreve em {@code movies} em tempo de execução. Enquanto isso for verdade, o resultado calculado
 * uma vez nunca fica desatualizado e não há expiração nem invalidação.
 *
 * <p><b>Para aceitar escrita em runtime</b> (não implementado): {@code movies} passaria a publicar um evento na sua
 * API pública a cada alteração confirmada (por exemplo {@code MoviesChanged}, via {@code ApplicationEventPublisher}
 * depois do commit), e este componente o ouviria para descartar o valor ({@code cached = null}), deixando o próximo
 * {@link #get()} recalcular. Como o evento é da API de {@code movies}, a dependência continua sendo só
 * {@code awards -> movies}.
 */
@Component
class AwardIntervalsCache {

    private final ProducerWins producerWins;
    private final AwardIntervalsCalculator calculator;

    private volatile AwardIntervals cached;

    AwardIntervalsCache(ProducerWins producerWins, AwardIntervalsCalculator calculator) {
        this.producerWins = producerWins;
        this.calculator = calculator;
    }

    AwardIntervals get() {
        AwardIntervals result = cached;
        if (result == null) {
            synchronized (this) {
                result = cached;
                if (result == null) {
                    result = calculator.calculate(producerWins.distinctWins());
                    cached = result;
                }
            }
        }
        return result;
    }
}
