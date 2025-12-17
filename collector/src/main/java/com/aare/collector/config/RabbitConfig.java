package com.aare.collector.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    // For consuming events from Gateway
    public static final String API_EVENTS_EXCHANGE = "api.events.exchange";
    public static final String API_EVENTS_QUEUE = "api.events.queue";
    public static final String API_EVENTS_ROUTING_KEY = "api.events.routingkey";

    // For publishing events to Analyzer
    public static final String API_ANALYSIS_EXCHANGE = "api.analysis.exchange";
    public static final String API_ANALYSIS_QUEUE = "api.analysis.queue";
    public static final String API_ANALYSIS_ROUTING_KEY = "api.analysis.routingkey";


    // Consume from api.events
    @Bean
    public TopicExchange apiEventsExchange() {
        return new TopicExchange(API_EVENTS_EXCHANGE);
    }

    @Bean
    public Queue apiEventsQueue() {
        return new Queue(API_EVENTS_QUEUE, true); // durable
    }

    @Bean
    public Binding apiEventsBinding(Queue apiEventsQueue, TopicExchange apiEventsExchange) {
        return BindingBuilder.bind(apiEventsQueue).to(apiEventsExchange).with(API_EVENTS_ROUTING_KEY);
    }

    // Publish to api.analysis
    @Bean
    public TopicExchange apiAnalysisExchange() {
        return new TopicExchange(API_ANALYSIS_EXCHANGE);
    }

    @Bean
    public Queue apiAnalysisQueue() {
        return new Queue(API_ANALYSIS_QUEUE, true); // durable
    }

    @Bean
    public Binding apiAnalysisBinding(Queue apiAnalysisQueue, TopicExchange apiAnalysisExchange) {
        return BindingBuilder.bind(apiAnalysisQueue).to(apiAnalysisExchange).with(API_ANALYSIS_ROUTING_KEY);
    }


    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public AmqpTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        final RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(jsonMessageConverter());
        return rabbitTemplate;
    }
}
