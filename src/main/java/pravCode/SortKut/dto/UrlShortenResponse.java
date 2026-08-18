package pravCode.SortKut.dto;

import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UrlShortenResponse {
    private String shortCode;
    private Long clickCount;
    private Long clicksToday;
    private LocalDateTime lastClickAt;
}
