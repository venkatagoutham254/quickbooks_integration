package aforo.quickbooks.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for QuickBooks OAuth token response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuickBooksTokenResponse {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("refresh_token")
    private String refreshToken;

    @JsonProperty("token_type")
    private String tokenType;

    @JsonProperty("expires_in")
    private Long expiresIn;  // Seconds

    @JsonProperty("x_refresh_token_expires_in")
    private Long refreshTokenExpiresIn;  // Seconds
}
