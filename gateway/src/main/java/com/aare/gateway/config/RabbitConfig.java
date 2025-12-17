package com.aare.gateway.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class RabbitConfig {

    public static final String API_EVENTS_EXCHANGE = "api.events.exchange";
    public static final String API_EVENTS_QUEUE = "api.events.queue";
    public static final String API_EVENTS_ROUTING_KEY = "api.events.routingkey";

    @Bean
    public TopicExchange apiEventsExchange() {
        return new TopicExchange(API_EVENTS_EXCHANGE);
    }

    @Bean
    public Queue apiEventsQueue() {
        return new Queue(API_EVENTS_QUEUE, true); // durable
    }

    @Bean
    public Binding apiEventsBinding(
            Queue apiEventsQueue,
            TopicExchange apiEventsExchange
    ) {
        return BindingBuilder
                .bind(apiEventsQueue)
                .to(apiEventsExchange)
                .with(API_EVENTS_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * Custom RabbitTemplate for Gateway publishing.
     * Renamed to avoid conflict with Spring Boot auto-configured rabbitTemplate.
     */
    @Bean(name = "gatewayRabbitTemplate")
    @Primary
    public RabbitTemplate gatewayRabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}
