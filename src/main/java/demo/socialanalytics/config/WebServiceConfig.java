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

// Dịch vụ SOAP.
//
// Đặt ở /soap/* chứ KHÔNG phải /ws/* như hướng dẫn Spring-WS thường viết: /ws đã là endpoint
// WebSocket của dashboard, hai thứ trùng đường dẫn thì servlet này nuốt luôn cả request
// WebSocket và phần realtime chết.
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
    // Địa chỉ: /api/v1/soap/socialAnalytics.wsdl
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

    // Ánh xạ ngoại lệ sang MÃ LỖI SOAP đúng ngữ nghĩa.
    //
    // Mặc định Spring-WS trả Server fault cho mọi ngoại lệ. Điều đó sai với lỗi do người gọi
    // gây ra (sai tiền tệ, sai nền tảng): client SOAP đọc mã Server là "lỗi tạm thời phía bạn,
    // cứ thử lại" nên sẽ retry mãi một request không bao giờ đúng được.
    // Client fault nói đúng bản chất: sửa request đi rồi hãy gọi lại.
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
    // contextPath là gói chứa các lớp JAXB sinh ra từ XSD.
    @Bean
    public Jaxb2Marshaller jaxb2Marshaller() {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setContextPath("demo.socialanalytics.ws");
        return marshaller;
    }
}
