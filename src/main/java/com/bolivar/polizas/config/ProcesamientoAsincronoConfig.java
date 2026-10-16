package com.bolivar.polizas.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Habilita el sondeo del outbox y el disparo asíncrono tras commit. Con
 * {@code polizas.integracion.procesador.habilitado=false} (p. ej. en algunas pruebas) las
 * operaciones quedan PENDIENTE hasta que se invoque el procesador explícitamente.
 * No requiere broker externo: el outbox en PostgreSQL es la cola durable (alternativa
 * ejecutable localmente a un broker).
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
@EnableScheduling
@ConditionalOnProperty(name = "polizas.integracion.procesador.habilitado", havingValue = "true", matchIfMissing = true)
public class ProcesamientoAsincronoConfig {

    @Bean(name = "procesadorExecutor")
    public ThreadPoolTaskExecutor procesadorExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("outbox-");
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(200);
        // Si la cola se llena se descarta el disparo: el sondeo periódico procesará la operación.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.DiscardPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(15);
        executor.initialize();
        return executor;
    }
}
