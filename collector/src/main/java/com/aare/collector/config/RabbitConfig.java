package com.aare.collector.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;

@Configuration
public class RabbitConfig {

    // ✅ Constants used by listeners/publishers
    public static final String API_EVENTS_EXCHANGE = "api.events.exchange";

    public static final String API_EVENTS_QUEUE = "api.events.queue";
    public static final String API_ANALYSIS_QUEUE = "api.analysis.queue";

    public static final String API_EVENTS_ROUTING_KEY = "api.events.#";
    public static final String API_ANALYSIS_ROUTING_KEY = "api.analysis.#";

    /* =========================
       Exchange
       ========================= */
    @Bean
    public TopicExchange apiEventsExchange() {
        return new TopicExchange(API_EVENTS_EXCHANGE, true, false);
    }

    /* =========================
       Queues
       ========================= */
    @Bean
    public Queue apiEventsQueue() {
        return new Queue(API_EVENTS_QUEUE, true);
    }

    @Bean
    public Queue apiAnalysisQueue() {
        return new Queue(API_ANALYSIS_QUEUE, true);
    }

    /* =========================
       Bindings
       ========================= */

    @Bean
    public Binding apiEventsBinding(
            @Qualifier("apiEventsQueue") Queue queue,
            TopicExchange apiEventsExchange
    ) {
        return BindingBuilder
                .bind(queue)
                .to(apiEventsExchange)
                .with(API_EVENTS_ROUTING_KEY);
    }

    @Bean
    public Binding apiAnalysisBinding(
            @Qualifier("apiAnalysisQueue") Queue queue,
            TopicExchange apiEventsExchange
    ) {
        return BindingBuilder
                .bind(queue)
                .to(apiEventsExchange)
                .with(API_ANALYSIS_ROUTING_KEY);
    }
}
