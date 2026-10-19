package com.outsera.awards;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.outsera.movies.ProducerWin;

/**
 * Calcula, numa única passada, os menores e maiores intervalos entre vitórias adjacentes de cada produtor.
 * Empates são todos mantidos; produtores com uma só vitória não geram intervalo.
 */
@Component
class AwardIntervalsCalculator {

    private static final Comparator<ProducerInterval> RESPONSE_ORDER =
            Comparator.comparingInt(ProducerInterval::previousWin).thenComparing(ProducerInterval::producer);

    /**
     * @param wins vitórias distintas (produtor, ano), ordenadas por produtor e ano (contrato de {@code ProducerWins})
     */
    AwardIntervals calculate(List<ProducerWin> wins) {
        List<ProducerInterval> min = new ArrayList<>();
        List<ProducerInterval> max = new ArrayList<>();

        ProducerWin previous = null;
        for (ProducerWin win : wins) {
            if (previous != null && previous.producer().equals(win.producer())) {
                var interval = new ProducerInterval(win.producer(), win.year() - previous.year(), previous.year(), win.year());
                keepExtreme(min, interval, Comparator.naturalOrder());
                keepExtreme(max, interval, Comparator.reverseOrder());
            }
            previous = win;
        }
        return new AwardIntervals(sorted(min), sorted(max));
    }

    /** Mantém em {@code kept} só os intervalos "melhores" segundo {@code order}: substitui, acumula empates ou descarta. */
    private static void keepExtreme(List<ProducerInterval> kept, ProducerInterval candidate, Comparator<Integer> order) {
        int comparison = kept.isEmpty() ? -1 : order.compare(candidate.interval(), kept.get(0).interval());
        if (comparison < 0) {
            kept.clear();
        }
        if (comparison <= 0) {
            kept.add(candidate);
        }
    }

    private static List<ProducerInterval> sorted(List<ProducerInterval> intervals) {
        return intervals.stream().sorted(RESPONSE_ORDER).toList();
    }
}
