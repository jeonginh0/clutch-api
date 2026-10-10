package jih.backend.global.config;

import java.util.List;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@ConfigurationProperties(prefix = "clutch.web")
public class ClutchWebProperties {

    private final List<String> corsAllowedOrigins;

    public ClutchWebProperties(List<String> corsAllowedOrigins) {
        this.corsAllowedOrigins = List.copyOf(corsAllowedOrigins);
    }
}