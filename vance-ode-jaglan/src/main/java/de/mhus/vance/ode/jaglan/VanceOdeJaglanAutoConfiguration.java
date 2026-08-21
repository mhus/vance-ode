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
package de.mhus.vance.ode.jaglan;

import de.mhus.vance.ode.inbound.OdeAuthService;
import de.mhus.vance.ode.inbound.OdeInboundSecurity;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Exposes the file endpoint once the application provides a {@link FileSource}.
 *
 * <p>Opt-in by presence, like the rest of Ode: the bean is the switch. A
 * library that starts serving files merely by being on the classpath would be a
 * bad neighbour in software it was only embedded in — and this endpoint hands
 * out file contents, which makes an unwanted one worse than most.
 *
 * <p>Deliberately <b>not</b> conditional on {@code vance.ode.base-url}: that
 * property says where to reach a brain, and answering a request needs no brain.
 */
@AutoConfiguration
@ConditionalOnClass(DispatcherServlet.class)
@ConditionalOnBean(FileSource.class)
@EnableConfigurationProperties(VanceOdeJaglanProperties.class)
public class VanceOdeJaglanAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OdeFileController odeFileController(
            FileSource source, VanceOdeJaglanProperties properties) {
        return new OdeFileController(source, properties);
    }

    /**
     * Registers the token check on the configured path: against the
     * application's {@link OdeAuthService} if it published one, against the
     * configured shared secret otherwise, and not at all when there is neither.
     * The guard itself is shared with every other inbound module
     * ({@code de.mhus.vance.ode.inbound}) — an authentication check duplicated
     * per endpoint drifts.
     */
    @Bean
    @ConditionalOnMissingBean(name = "odeFileSecurityConfigurer")
    public WebMvcConfigurer odeFileSecurityConfigurer(
            VanceOdeJaglanProperties properties,
            ObjectProvider<OdeAuthService> authService) {
        return OdeInboundSecurity.guarding(properties, authService.getIfAvailable());
    }
}
