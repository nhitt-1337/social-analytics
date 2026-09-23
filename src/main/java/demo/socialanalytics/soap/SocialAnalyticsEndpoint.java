package demo.socialanalytics.soap;

import demo.socialanalytics.config.WebServiceConfig;
import demo.socialanalytics.entity.Platform;
import demo.socialanalytics.service.ExchangeRateService;
import demo.socialanalytics.service.StatisticsService;
import demo.socialanalytics.util.PlatformParser;
import demo.socialanalytics.ws.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

import java.time.LocalDateTime;

// Endpoint SOAP.
@Endpoint
public class SocialAnalyticsEndpoint {

    private static final Logger log = LoggerFactory.getLogger(SocialAnalyticsEndpoint.class);

    private final StatisticsService statisticsService;
    private final ExchangeRateService exchangeRateService;

    public SocialAnalyticsEndpoint(
        StatisticsService statisticsService, ExchangeRateService exchangeRateService) {
        this.statisticsService = statisticsService;
        this.exchangeRateService = exchangeRateService;
    }

    @PayloadRoot(namespace = WebServiceConfig.NAMESPACE, localPart = "getPlatformSummaryRequest")
    @ResponsePayload
    public GetPlatformSummaryResponse getPlatformSummary(
        @RequestPayload GetPlatformSummaryRequest request) {

        Platform filter = PlatformParser.parse(request.getPlatform());
        log.info("SOAP getPlatformSummary, lọc theo {}", filter == null ? "tất cả" : filter);

        GetPlatformSummaryResponse response = new GetPlatformSummaryResponse();
        statisticsService.current().stream()
            .filter(summary -> filter == null || summary.getPlatform() == filter)
            .map(this::toSoapSummary)
            .forEach(response.getSummary()::add);
        response.setGeneratedAt(LocalDateTime.now());
        return response;
    }

    // Dịch vụ tỷ giá giả lập
    @PayloadRoot(namespace = WebServiceConfig.NAMESPACE, localPart = "getExchangeRateRequest")
    @ResponsePayload
    public GetExchangeRateResponse getExchangeRate(@RequestPayload GetExchangeRateRequest request) {
        log.info("SOAP getExchangeRate {} -> {}", request.getFromCurrency(), request.getToCurrency());
        return exchangeRateService.quote(request.getFromCurrency(), request.getToCurrency());
    }

    private PlatformSummary toSoapSummary(demo.socialanalytics.entity.PlatformSummary summary) {
        PlatformSummary soap = new PlatformSummary();
        soap.setPlatform(summary.getPlatform().getSlug());
        soap.setPostCount(summary.getPostCount());
        soap.setAccountCount(summary.getAccountCount());
        soap.setUpdatedAt(summary.getUpdatedAt());
        return soap;
    }
}
