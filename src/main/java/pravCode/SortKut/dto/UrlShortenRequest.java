package pravCode.SortKut.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UrlShortenRequest {

    @NotBlank(message = "URL cannot be blank")
    private String originalUrl;

    private String customAlias;

    private String expiresIn;

    private String password;
}
