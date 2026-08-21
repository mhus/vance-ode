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
package de.mhus.vance.ode.kit;

import de.mhus.vance.ode.inbound.OdeAuthService;
import de.mhus.vance.ode.inbound.OdeInboundSecurity;
import java.util.List;
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
 * Exposes the kit endpoints once the application publishes a {@link KitSource}.
 *
 * <p>Opt-in by presence, like the rest of Ode: the bean is the switch. That
 * matters more here than for a read-only endpoint — a kit carries tool
 * definitions, so serving one by accident hands a reachable capability surface
 * to whoever finds the path.
 *
 * <p>Deliberately <b>not</b> conditional on {@code vance.ode.base-url}: that
 * property says where to reach a brain, and answering a request needs no brain.
 */
@AutoConfiguration
@ConditionalOnClass(DispatcherServlet.class)
@ConditionalOnBean(KitSource.class)
@EnableConfigurationProperties(VanceOdeKitProperties.class)
public class VanceOdeKitAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OdeKitController odeKitController(
            List<KitSource> sources, VanceOdeKitProperties properties) {
        return new OdeKitController(sources, properties);
    }

    /**
     * Registers the token check on the configured path: against the
     * application's {@link OdeAuthService} if it published one, against the
     * configured shared secret otherwise, and not at all when there is neither.
     * The guard is shared with every other inbound module — an authentication
     * check duplicated per endpoint drifts.
     */
    @Bean
    @ConditionalOnMissingBean(name = "odeKitSecurityConfigurer")
    public WebMvcConfigurer odeKitSecurityConfigurer(
            VanceOdeKitProperties properties, ObjectProvider<OdeAuthService> authService) {
        return OdeInboundSecurity.guarding(properties, authService.getIfAvailable());
    }
}
