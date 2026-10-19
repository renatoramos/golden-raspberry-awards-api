package com.outsera.awards;

import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Aquece o {@link AwardIntervalsCache} <b>antes</b> de o servidor web abrir a porta, para que nenhuma requisição pague
 * o cálculo.
 *
 * <p>É um {@code SmartLifecycle} de fase baixa, e não um {@code SmartInitializingSingleton}, de propósito: os
 * callbacks de {@code SmartInitializingSingleton} rodam na ordem de registro dos beans (aqui {@code awards} vem antes de
 * {@code movies}) e o aquecimento poderia rodar antes da carga do CSV, guardando um resultado vazio. Os
 * {@code SmartLifecycle} só iniciam depois que todos os singletons e seus callbacks (inclusive a carga do CSV) terminaram,
 * e a fase 0 vem antes da do servidor web, que inicia perto do fim. Se o cálculo falhar, a aplicação não sobe.
 */
@Component
class AwardIntervalsWarmUp implements SmartLifecycle {

    /** Antes do servidor web (fase próxima de {@code Integer.MAX_VALUE}); depois da carga, que não é lifecycle. */
    static final int PHASE = 0;

    private final AwardIntervalsCache cache;
    private volatile boolean running;

    AwardIntervalsWarmUp(AwardIntervalsCache cache) {
        this.cache = cache;
    }

    @Override
    public void start() {
        cache.get();
        running = true;
    }

    @Override
    public void stop() {
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return PHASE;
    }
}
