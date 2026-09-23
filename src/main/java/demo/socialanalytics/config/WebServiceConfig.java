package demo.socialanalytics.config;

import demo.socialanalytics.soap.ExchangeRateProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.config.annotation.EnableWs;
import org.springframework.ws.soap.server.endpoint.SoapFaultDefinition;
import org.springframework.ws.soap.server.endpoint.SoapFaultMappingExceptionResolver;
import org.springframework.ws.transport.http.MessageDispatcherServlet;
import org.springframework.ws.wsdl.wsdl11.DefaultWsdl11Definition;
import org.springframework.xml.xsd.SimpleXsdSchema;
import org.springframework.xml.xsd.XsdSchema;

// Không đặt ở /ws vì trùng endpoint WebSocket của dashboard
@Configuration
@EnableWs
@EnableConfigurationProperties(ExchangeRateProperties.class)
public class WebServiceConfig {

    public static final String PATH = "/soap";
    public static final String NAMESPACE = "http://demo/socialanalytics/ws";

    @Bean
    public ServletRegistrationBean<MessageDispatcherServlet> messageDispatcherServlet(
        ApplicationContext applicationContext) {

        MessageDispatcherServlet servlet = new MessageDispatcherServlet();
        servlet.setApplicationContext(applicationContext);
        // Cho servlet tự phục vụ WSDL tại /soap/<tên>.wsdl
        servlet.setTransformWsdlLocations(true);
        return new ServletRegistrationBean<>(servlet, PATH + "/*");
    }

    // WSDL sinh tự động từ chính XSD đang dùng, nên tài liệu không bao giờ lệch với thực tế.
    @Bean(name = "socialAnalytics")
    public DefaultWsdl11Definition wsdlDefinition(XsdSchema socialAnalyticsSchema) {
        DefaultWsdl11Definition definition = new DefaultWsdl11Definition();
        definition.setPortTypeName("SocialAnalyticsPort");
        definition.setLocationUri(PATH);
        definition.setTargetNamespace(NAMESPACE);
        definition.setSchema(socialAnalyticsSchema);
        return definition;
    }

    @Bean
    public XsdSchema socialAnalyticsSchema() {
        return new SimpleXsdSchema(new ClassPathResource("xsd/social-analytics.xsd"));
    }

    // Lỗi do người gọi -> Client fault, để client không retry vô ích
    @Bean
    public SoapFaultMappingExceptionResolver soapFaultResolver() {
        SoapFaultMappingExceptionResolver resolver = new SoapFaultMappingExceptionResolver();

        SoapFaultDefinition clientFault = new SoapFaultDefinition();
        clientFault.setFaultCode(SoapFaultDefinition.CLIENT);

        java.util.Properties mappings = new java.util.Properties();
        mappings.setProperty(
            "demo.socialanalytics.exception.InvalidRequestParameterException", "CLIENT");
        mappings.setProperty(
            "demo.socialanalytics.exception.ResourceNotFoundException", "CLIENT");
        resolver.setExceptionMappings(mappings);

        // Còn lại giữ Server fault: đó thực sự là lỗi phía mình.
        SoapFaultDefinition serverFault = new SoapFaultDefinition();
        serverFault.setFaultCode(SoapFaultDefinition.SERVER);
        resolver.setDefaultFault(serverFault);

        // Chạy trước bộ xử lý mặc định.
        resolver.setOrder(1);
        return resolver;
    }

    // Marshaller dùng chung cho cả phía tạo và phía tiêu thụ SOAP.
    @Bean
    public Jaxb2Marshaller jaxb2Marshaller() {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setContextPath("demo.socialanalytics.ws");
        return marshaller;
    }
}
