package demo.socialanalytics.dto.response;

public record UserSummaryResponse(
    Long id,
    String fullName,
    String email
) {
}
