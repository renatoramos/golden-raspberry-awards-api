package com.outsera;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Estrutura dos módulos: awards só pode depender da API pública de movies; sem ciclos nem acesso a internos.
 */
class ModularityTest {

    @Test
    void modulesRespectTheirBoundaries() {
        ApplicationModules.of(MoviesApplication.class).verify();
    }
}
