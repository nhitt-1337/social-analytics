package demo.socialanalytics.dto.response;

import java.util.List;

public record ImportResultResponse(
    int totalRows,
    int imported,
    int skipped,
    List<RowErrorResponse> errors
) {
    public record RowErrorResponse(int rowNumber, String message) {
    }
}
