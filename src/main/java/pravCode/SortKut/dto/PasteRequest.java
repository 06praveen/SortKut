package pravCode.SortKut.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PasteRequest {

    @NotBlank(message = "Content cannot be empty")
    private String content;

    private String title;

    @NotBlank(message = "Language cannot be empty")
    private String language;

    @NotBlank(message = "Expiration time cannot be empty")
    private String expiresIn;

    private String password;
}
