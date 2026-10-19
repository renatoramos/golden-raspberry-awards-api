/**
 * Regra de negócio dos intervalos entre prêmios e o endpoint REST. Depende apenas da API pública de {@code movies}.
 */
@ApplicationModule(allowedDependencies = "movies")
package com.outsera.awards;

import org.springframework.modulith.ApplicationModule;
