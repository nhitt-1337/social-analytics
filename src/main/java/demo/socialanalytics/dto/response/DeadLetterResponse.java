package demo.socialanalytics.dto.response;

import demo.socialanalytics.entity.DeadLetter;

import java.time.LocalDateTime;

public record DeadLetterResponse(
    Long id,
    String sourceQueue,
    String messageId,
    String payload,
    String failureCause,
    LocalDateTime receivedAt
) {
    public static DeadLetterResponse of(DeadLetter deadLetter) {
        return new DeadLetterResponse(
            deadLetter.getId(),
            deadLetter.getSourceQueue(),
            deadLetter.getMessageId(),
            deadLetter.getPayload(),
            deadLetter.getFailureCause(),
            deadLetter.getReceivedAt());
    }
}
