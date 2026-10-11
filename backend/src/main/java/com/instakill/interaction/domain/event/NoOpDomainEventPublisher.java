package com.instakill.interaction.domain.event;

import com.instakill.interaction.domain.DomainEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class NoOpDomainEventPublisher implements DomainEventPublisher {

    @Override
    public void publish(Object event) {
        // No operation
    }
}
