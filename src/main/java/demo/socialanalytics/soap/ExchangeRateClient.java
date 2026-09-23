package demo.socialanalytics.soap;

import demo.socialanalytics.ws.GetExchangeRateRequest;
import demo.socialanalytics.ws.GetExchangeRateResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.stereotype.Component;
import org.springframework.ws.client.WebServiceIOException;
import org.springframework.ws.client.core.WebServiceTemplate;
import org.springframework.ws.soap.client.SoapFaultClientException;

// Phía TIÊU THỤ SOAP: gọi ra một dịch vụ tỷ giá bên ngoài.
@Component
public class ExchangeRateClient {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateClient.class);

    private final WebServiceTemplate webServiceTemplate;
    private final ExchangeRateProperties properties;

    public ExchangeRateClient(Jaxb2Marshaller jaxb2Marshaller, ExchangeRateProperties properties) {
        this.properties = properties;
        // Cùng một marshaller cho cả hai chiều gửi và nhận.
        this.webServiceTemplate = new WebServiceTemplate(jaxb2Marshaller);
        this.webServiceTemplate.setUnmarshaller(jaxb2Marshaller);
        this.webServiceTemplate.setDefaultUri(properties.endpoint());
    }

    public GetExchangeRateResponse fetchRate(String from, String to) {
        GetExchangeRateRequest request = new GetExchangeRateRequest();
        request.setFromCurrency(from);
        request.setToCurrency(to);

        try {
            GetExchangeRateResponse response = (GetExchangeRateResponse)
                webServiceTemplate.marshalSendAndReceive(properties.endpoint(), request);
            log.debug("Tỷ giá {} -> {} = {}", from, to, response.getRate());
            return response;
        } catch (SoapFaultClientException exception) {
            // SOAP Fault là LỖI NGHIỆP VỤ do nhà cung cấp trả về (tiền tệ không hỗ trợ...)
            throw new ExchangeRateUnavailableException(
                "Dịch vụ tỷ giá từ chối yêu cầu: " + exception.getFaultStringOrReason(), exception);
        } catch (WebServiceIOException exception) {
            // Không gọi được tới nơi (sập, timeout, sai địa chỉ).
            throw new ExchangeRateUnavailableException(
                "Không kết nối được tới dịch vụ tỷ giá: " + exception.getMessage(), exception);
        }
    }

    // Lộ ra để test trỏ được sang server SOAP giả lập (MockWebServiceServer).
    public WebServiceTemplate webServiceTemplate() {
        return webServiceTemplate;
    }
}
