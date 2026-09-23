package demo.socialanalytics.service;

import demo.socialanalytics.exception.InvalidRequestParameterException;
import demo.socialanalytics.ws.GetExchangeRateResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;

@Service
public class ExchangeRateService {
    private static final Map<String, BigDecimal> PER_USD = Map.of(
        "USD", new BigDecimal("1"),
        "VND", new BigDecimal("25400"),
        "EUR", new BigDecimal("0.92"),
        "JPY", new BigDecimal("157.5"),
        "SGD", new BigDecimal("1.35")
    );

    public GetExchangeRateResponse quote(String from, String to) {
        String source = normalize(from);
        String target = normalize(to);

        BigDecimal sourcePerUsd = rateOf(source);
        BigDecimal targetPerUsd = rateOf(target);

        BigDecimal rate = targetPerUsd.divide(sourcePerUsd, 6, java.math.RoundingMode.HALF_UP);

        GetExchangeRateResponse response = new GetExchangeRateResponse();
        response.setFromCurrency(source);
        response.setToCurrency(target);
        response.setRate(rate);
        response.setQuotedAt(LocalDateTime.now());
        return response;
    }

    public java.util.Set<String> supportedCurrencies() {
        return PER_USD.keySet();
    }

    private BigDecimal rateOf(String currency) {
        BigDecimal rate = PER_USD.get(currency);
        if (rate == null) {
            throw new InvalidRequestParameterException(
                "Chưa hỗ trợ tiền tệ " + currency + ". Cho phép: "
                    + PER_USD.keySet().stream().sorted().toList());
        }
        return rate;
    }

    private String normalize(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new InvalidRequestParameterException("Mã tiền tệ không được để trống");
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }
}
