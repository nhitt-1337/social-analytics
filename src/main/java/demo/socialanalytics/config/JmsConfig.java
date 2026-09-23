package demo.socialanalytics.config;

import jakarta.jms.ConnectionFactory;
import org.apache.activemq.RedeliveryPolicy;
import org.springframework.boot.activemq.autoconfigure.ActiveMQConnectionFactoryCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.support.converter.JacksonJsonMessageConverter;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.converter.MessageType;

@Configuration
@EnableJms
@EnableConfigurationProperties(MessagingProperties.class)
public class JmsConfig {

    public static final String LISTENER_FACTORY = "jmsListenerContainerFactory";

    private final MessagingProperties properties;

    public JmsConfig(MessagingProperties properties) {
        this.properties = properties;
    }

    // Gửi và nhận đều dùng JSON (TextMessage) thay vì Java serialization.
    @Bean
    public MessageConverter jmsMessageConverter() {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
        converter.setTargetType(MessageType.TEXT);
        // Kiểu message nằm ở property "_type" để bên nhận biết đường chuyển đổi.
        converter.setTypeIdPropertyName("_type");
        return converter;
    }

    // Thử lại rồi mới bỏ vào DLQ.
    @Bean
    public ActiveMQConnectionFactoryCustomizer redeliveryPolicyCustomizer() {
        return factory -> {
            RedeliveryPolicy policy = new RedeliveryPolicy();
            policy.setMaximumRedeliveries(properties.maxRedeliveries());
            policy.setInitialRedeliveryDelay(properties.initialRedeliveryDelayMs());
            policy.setUseExponentialBackOff(true);
            policy.setBackOffMultiplier(properties.backOffMultiplier());
            factory.setRedeliveryPolicy(policy);
        };
    }

    @Bean(name = LISTENER_FACTORY)
    public DefaultJmsListenerContainerFactory jmsListenerContainerFactory(
        ConnectionFactory connectionFactory, MessageConverter jmsMessageConverter) {

        DefaultJmsListenerContainerFactory factory = new DefaultJmsListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(jmsMessageConverter);

        // Bắt buộc cho cơ chế thử lại: listener ném lỗi thì rollback, message quay lại hàng đợi
        factory.setSessionTransacted(true);

        // Nhiều listener chạy song song; hàng đợi dồn thì tự nâng lên tới 5.
        factory.setConcurrency("1-5");
        return factory;
    }
}
