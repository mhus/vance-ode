package de.mhus.vance.ode.core;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Wires the transport when — and only when — a brain URL is configured.
 *
 * <p>{@code matchIfMissing = false} is the point: putting Ode on the
 * classpath must not by itself create a client aimed at a server. An
 * application that adds the dependency and configures nothing gets
 * nothing, which is the behaviour a library owes software it was merely
 * embedded in.
 */
@AutoConfiguration
@EnableConfigurationProperties(VanceOdeProperties.class)
@ConditionalOnProperty(prefix = "vance.ode", name = "base-url")
public class VanceOdeCoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OdeHttpTransport odeHttpTransport(VanceOdeProperties properties) {
        return new OdeHttpTransport(properties);
    }
}
