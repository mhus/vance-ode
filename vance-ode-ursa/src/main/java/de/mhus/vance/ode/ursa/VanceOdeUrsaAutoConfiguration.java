package de.mhus.vance.ode.ursa;

import de.mhus.vance.ode.core.OdeHttpTransport;
import de.mhus.vance.ode.core.VanceOdeCoreAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Contributes the event client once the core transport exists.
 *
 * <p>Conditional on the transport bean rather than on a property of its
 * own: the core auto-configuration already decided whether Ode is
 * configured at all, and repeating that condition here would let the two
 * drift apart.
 */
@AutoConfiguration(after = VanceOdeCoreAutoConfiguration.class)
@ConditionalOnBean(OdeHttpTransport.class)
public class VanceOdeUrsaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public UrsaEventClient ursaEventClient(OdeHttpTransport transport) {
        return new UrsaEventClient(transport);
    }
}
