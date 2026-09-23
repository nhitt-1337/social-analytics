package demo.socialanalytics.controller;

import demo.socialanalytics.soap.ExchangeRateClient;
import demo.socialanalytics.ws.GetExchangeRateResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Tag(name = "Exchange rate", description = "Tỷ giá, lấy qua WebService SOAP")
@RestController
public class ExchangeRateController {
    private final ExchangeRateClient exchangeRateClient;

    public ExchangeRateController(ExchangeRateClient exchangeRateClient) {
        this.exchangeRateClient = exchangeRateClient;
    }

    public record ExchangeRateResponse(
        String from, String to, BigDecimal rate, LocalDateTime quotedAt) {
    }

    @Operation(summary = "Lấy tỷ giá qua SOAP",
        description = "Gọi WebService SOAP cấu hình ở social.exchange-rate.endpoint. "
            + "503 khi dịch vụ đó không dùng được.")
    @GetMapping("/exchange-rate")
    public ResponseEntity<ExchangeRateResponse> exchangeRate(
        @RequestParam(defaultValue = "USD") String from,
        @RequestParam(defaultValue = "VND") String to
    ) {
        GetExchangeRateResponse quote = exchangeRateClient.fetchRate(from, to);
        return ResponseEntity.ok(new ExchangeRateResponse(
            quote.getFromCurrency(), quote.getToCurrency(), quote.getRate(), quote.getQuotedAt()));
    }
}
