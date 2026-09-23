package demo.socialanalytics.soap;

import demo.socialanalytics.ws.GetExchangeRateResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.ws.test.client.MockWebServiceServer;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.ws.test.client.RequestMatchers.*;
import static org.springframework.ws.test.client.ResponseCreators.*;

// Phía TIÊU THỤ SOAP.
//
// MockWebServiceServer chặn ngay ở tầng WebServiceTemplate: kiểm tra được XML gửi đi và giả lập
// XML trả về mà không cần mở cổng mạng nào — quan trọng vì cái đáng kiểm ở đây là phần dịch
// đối tượng Java sang XML và ngược lại, không phải HTTP.
@SpringBootTest
@ActiveProfiles("test")
class ExchangeRateClientTest {

    private static final String NS = "http://demo/socialanalytics/ws";

    @Autowired ExchangeRateClient client;

    private MockWebServiceServer server;

    @BeforeEach
    void setUp() {
        server = MockWebServiceServer.createServer(client.webServiceTemplate());
    }

    private ByteArrayResource xml(String body) {
        return new ByteArrayResource(body.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void guiDungXmlVaDocDuocKetQuaTraVe() throws Exception {
        server.expect(payload(xml("""
                <getExchangeRateRequest xmlns="%s">
                    <fromCurrency>USD</fromCurrency>
                    <toCurrency>VND</toCurrency>
                </getExchangeRateRequest>
                """.formatted(NS))))
            .andRespond(withPayload(xml("""
                <getExchangeRateResponse xmlns="%s">
                    <fromCurrency>USD</fromCurrency>
                    <toCurrency>VND</toCurrency>
                    <rate>25400.000000</rate>
                    <quotedAt>2026-09-23T10:00:00</quotedAt>
                </getExchangeRateResponse>
                """.formatted(NS))));

        GetExchangeRateResponse response = client.fetchRate("USD", "VND");

        assertThat(response.getRate()).isEqualByComparingTo(new BigDecimal("25400"));
        assertThat(response.getFromCurrency()).isEqualTo("USD");
        assertThat(response.getQuotedAt()).isNotNull();
        server.verify();
    }

    // Nhà cung cấp trả SOAP Fault = lỗi NGHIỆP VỤ. Phải giữ lại thông điệp để bên trên hiểu
    // chuyện gì xảy ra, chứ không nuốt thành một lỗi chung chung.
    @Test
    void soapFaultDuocDoiThanhNgoaiLeCoThongDiepRoRang() throws Exception {
        server.expect(anything())
            .andRespond(withClientOrSenderFault("Tiền tệ XXX không được hỗ trợ", java.util.Locale.ENGLISH));

        assertThatThrownBy(() -> client.fetchRate("USD", "XXX"))
            .isInstanceOf(ExchangeRateUnavailableException.class)
            .hasMessageContaining("Tiền tệ XXX không được hỗ trợ");
    }

    @Test
    void khongGoiToiNoiThiBaoLoiKhongKetNoiDuoc() throws Exception {
        server.expect(anything()).andRespond(withException(new java.io.IOException("connection refused")));

        assertThatThrownBy(() -> client.fetchRate("USD", "VND"))
            .isInstanceOf(ExchangeRateUnavailableException.class)
            .hasMessageContaining("Không kết nối được");
    }

    // Namespace sai một chữ là cả hợp đồng SOAP hỏng — kiểm tra nó được gửi đúng.
    @Test
    void guiDungNamespaceTheoHopDong() throws Exception {
        server.expect(soapEnvelope(xml("""
                <SOAP-ENV:Envelope xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/">
                    <SOAP-ENV:Header/>
                    <SOAP-ENV:Body>
                        <getExchangeRateRequest xmlns="%s">
                            <fromCurrency>EUR</fromCurrency>
                            <toCurrency>JPY</toCurrency>
                        </getExchangeRateRequest>
                    </SOAP-ENV:Body>
                </SOAP-ENV:Envelope>
                """.formatted(NS))))
            .andRespond(withPayload(xml("""
                <getExchangeRateResponse xmlns="%s">
                    <fromCurrency>EUR</fromCurrency><toCurrency>JPY</toCurrency>
                    <rate>171.2</rate><quotedAt>2026-09-23T10:00:00</quotedAt>
                </getExchangeRateResponse>
                """.formatted(NS))));

        client.fetchRate("EUR", "JPY");

        server.verify();
    }
}
