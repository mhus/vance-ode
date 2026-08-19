/*
 * Copyright 2026 Mike Hummel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
