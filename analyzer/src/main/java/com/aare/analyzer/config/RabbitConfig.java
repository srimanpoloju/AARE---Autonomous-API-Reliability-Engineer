package com.aare.analyzer.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;

@Configuration
public class RabbitConfig {

    // For consuming events from Collector
    public static final String API_ANALYSIS_EXCHANGE = "api.analysis.exchange";
    public static final String API_ANALYSIS_QUEUE = "api.analysis.queue";
    public static final String API_ANALYSIS_ROUTING_KEY = "api.analysis.routingkey";

    // For publishing RCA requests
    public static final String RCA_REQUESTED_EXCHANGE = "rca.requested.exchange";
    public static final String RCA_REQUESTED_QUEUE = "rca.requested.queue";
    public static final String RCA_REQUESTED_ROUTING_KEY = "rca.requested.routingkey";

    // Consume from api.analysis
    @Bean
    public TopicExchange apiAnalysisExchange() {
        return new TopicExchange(API_ANALYSIS_EXCHANGE);
    }

    @Bean
    public Queue apiAnalysisQueue() {
        return new Queue(API_ANALYSIS_QUEUE, true);
    }

    @Bean
    public Binding apiAnalysisBinding(
            @Qualifier("apiAnalysisQueue") Queue apiAnalysisQueue,
            @Qualifier("apiAnalysisExchange") TopicExchange apiAnalysisExchange
    ) {
        return BindingBuilder.bind(apiAnalysisQueue)
                .to(apiAnalysisExchange)
                .with(API_ANALYSIS_ROUTING_KEY);
    }

    // Publish to rca.requested
    @Bean
    public TopicExchange rcaRequestedExchange() {
        return new TopicExchange(RCA_REQUESTED_EXCHANGE);
    }

    @Bean
    public Queue rcaRequestedQueue() {
        return new Queue(RCA_REQUESTED_QUEUE, true);
    }

    @Bean
    public Binding rcaRequestedBinding(
            @Qualifier("rcaRequestedQueue") Queue rcaRequestedQueue,
            @Qualifier("rcaRequestedExchange") TopicExchange rcaRequestedExchange
    ) {
        return BindingBuilder.bind(rcaRequestedQueue)
                .to(rcaRequestedExchange)
                .with(RCA_REQUESTED_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                        MessageConverter jsonMessageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter);
        return rabbitTemplate;
    }
}
