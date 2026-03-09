package it.giallocarbonara;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.support.converter.JacksonJsonMessageConverter;
import org.springframework.jms.support.converter.MessageType;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class JmsConfig {

    @Bean
    public JacksonJsonMessageConverter jacksonJmsMessageConverter() {
        // Create the modern JsonMapper (Jackson 3.x way)
        JsonMapper jsonMapper = JsonMapper.builder()
                .findAndAddModules()
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                // Instead of the enum, use the explicit configuration method
                .build();

        // Initialize the new converter
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter(jsonMapper);

        // Standard ActiveMQ/JMS settings
        converter.setTargetType(MessageType.TEXT);
        converter.setTypeIdPropertyName("_type");

        return converter;
    }
}