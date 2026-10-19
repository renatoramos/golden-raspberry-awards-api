package com.outsera.awards;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.config.BeanPostProcessor;

import com.outsera.movies.ProducerWins;

/** Embrulha o {@link ProducerWins} real contando as leituras; a consulta continua sendo a do H2. */
final class CountingProducerWins implements BeanPostProcessor {

    private final AtomicInteger calls;

    CountingProducerWins(AtomicInteger calls) {
        this.calls = calls;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof ProducerWins delegate) {
            return (ProducerWins) () -> {
                calls.incrementAndGet();
                return delegate.distinctWins();
            };
        }
        return bean;
    }
}
