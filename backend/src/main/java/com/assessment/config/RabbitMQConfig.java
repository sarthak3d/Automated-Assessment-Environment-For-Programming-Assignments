package com.assessment.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String PIPELINE_RESULTS_QUEUE = "pipeline.results";
    public static final String PIPELINE_RESULTS_EXCHANGE = "pipeline.results.exchange";
    public static final String PIPELINE_RESULTS_ROUTING_KEY = "pipeline.result";

    public static final String GRADING_QUEUE = "grading.requests";
    public static final String GRADING_EXCHANGE = "grading.exchange";
    public static final String GRADING_ROUTING_KEY = "grading.compute";

    public static final String DEAD_LETTER_QUEUE = "dead.letter.queue";
    public static final String DEAD_LETTER_EXCHANGE = "dead.letter.exchange";

    @Bean
    public Queue pipelineResultsQueue() {
        return QueueBuilder.durable(PIPELINE_RESULTS_QUEUE)
            .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
            .withArgument("x-dead-letter-routing-key", "dead.pipeline")
            .build();
    }

    @Bean
    public DirectExchange pipelineResultsExchange() {
        return new DirectExchange(PIPELINE_RESULTS_EXCHANGE);
    }

    @Bean
    public Binding pipelineResultsBinding() {
        return BindingBuilder
            .bind(pipelineResultsQueue())
            .to(pipelineResultsExchange())
            .with(PIPELINE_RESULTS_ROUTING_KEY);
    }

    @Bean
    public Queue gradingQueue() {
        return QueueBuilder.durable(GRADING_QUEUE)
            .withArgument("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE)
            .withArgument("x-dead-letter-routing-key", "dead.grading")
            .build();
    }

    @Bean
    public DirectExchange gradingExchange() {
        return new DirectExchange(GRADING_EXCHANGE);
    }

    @Bean
    public Binding gradingBinding() {
        return BindingBuilder
            .bind(gradingQueue())
            .to(gradingExchange())
            .with(GRADING_ROUTING_KEY);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE);
    }

    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder
            .bind(deadLetterQueue())
            .to(deadLetterExchange())
            .with("dead.#");
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}
