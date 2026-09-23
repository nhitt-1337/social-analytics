package demo.socialanalytics.soap;

import demo.socialanalytics.ws.GetExchangeRateRequest;
import demo.socialanalytics.ws.GetExchangeRateResponse;
import demo.socialanalytics.ws.GetPlatformSummaryRequest;
import demo.socialanalytics.ws.GetPlatformSummaryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.ws.client.core.WebServiceTemplate;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class SoapOverHttpTest {
    @LocalServerPort int port;
    @Autowired Jaxb2Marshaller marshaller;

    private WebServiceTemplate soapClient;

    @BeforeEach
    void setUp() {
        soapClient = new WebServiceTemplate(marshaller);
        soapClient.setUnmarshaller(marshaller);
        soapClient.setDefaultUri(endpoint());
    }

    private String endpoint() {
        return "http://localhost:" + port + "/api/v1/soap";
    }

    @Test
    void goiDuocSoapKhongCanDangNhap() {
        GetExchangeRateRequest request = new GetExchangeRateRequest();
        request.setFromCurrency("USD");
        request.setToCurrency("VND");

        GetExchangeRateResponse response =
            (GetExchangeRateResponse) soapClient.marshalSendAndReceive(endpoint(), request);

        assertThat(response.getRate()).isEqualByComparingTo(new BigDecimal("25400"));
    }

    @Test
    void goiDuocOperationThongKe() {
        GetPlatformSummaryResponse response = (GetPlatformSummaryResponse)
            soapClient.marshalSendAndReceive(endpoint(), new GetPlatformSummaryRequest());

        assertThat(response.getGeneratedAt()).isNotNull();
    }

    private HttpResponse<String> httpGet(String path) throws Exception {
        try (HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER).build()) {
            return client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                    .header("Accept", "application/json").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        }
    }

    @Test
    void taiDuocWsdlSinhTuXsd() throws Exception {
        HttpResponse<String> response = httpGet("/api/v1/soap/socialAnalytics.wsdl");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
            .contains("getExchangeRate")
            .contains("getPlatformSummary")
            .contains("http://demo/socialanalytics/ws");
    }

    @Test
    void mienTruChiApDungChoSoap() throws Exception {
        assertThat(httpGet("/api/v1/posts").statusCode()).isEqualTo(401);
        assertThat(httpGet("/api/v1/chart-data").statusCode()).isEqualTo(401);
        assertThat(httpGet("/api/v1/statistics").statusCode()).isEqualTo(401);
    }
}
