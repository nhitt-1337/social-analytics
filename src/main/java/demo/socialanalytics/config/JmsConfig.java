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
    //
    // Serialize kiểu Java trói message vào đúng class của ứng dụng: đổi tên gói là message cũ
    // trong hàng đợi thành rác, và service viết bằng ngôn ngữ khác thì không đọc nổi.
    @Bean
    public MessageConverter jmsMessageConverter() {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
        converter.setTargetType(MessageType.TEXT);
        // Kiểu message nằm ở property "_type" để bên nhận biết đường chuyển đổi.
        converter.setTypeIdPropertyName("_type");
        return converter;
    }

    // Thử lại rồi mới bỏ vào DLQ.
    //
    // Giãn cách tăng dần (0,5s → 1s → 2s): lỗi tạm thời như DB bận hay mạng chập chờn thường
    // tự hết sau một lúc, thử lại dồn dập chỉ làm tình hình tệ thêm.
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

        // BẮT BUỘC cho cơ chế thử lại: phiên có transaction thì listener ném lỗi sẽ rollback và
        // message quay lại hàng đợi. Để mặc định (AUTO_ACKNOWLEDGE) thì message coi như đã xử lý
        // xong ngay khi giao tới — lỗi là mất luôn, không thử lại và cũng không vào DLQ.
        factory.setSessionTransacted(true);

        // Nhiều listener chạy song song; hàng đợi dồn thì tự nâng lên tới 5.
        factory.setConcurrency("1-5");
        return factory;
    }
}
