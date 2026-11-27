package io.github.itech_mm.apiro.spring.webmvc;

import java.nio.charset.StandardCharsets;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@Controller
public class ApiroManifestController {
    public static final String PATH = "/apiro/manifest";
    public static final String TOKEN_HEADER = "X-Apiro-Token";

    private static final MediaType JSON_UTF8 = new MediaType("application", "json", StandardCharsets.UTF_8);

    private final ApiroManifestService service;
    private final ApiroSettings settings;

    public ApiroManifestController(ApiroManifestService service, ApiroSettings settings) {
        this.service = service;
        this.settings = settings;
    }

    @GetMapping(PATH)
    public ResponseEntity<byte[]> manifest(@RequestHeader(name = TOKEN_HEADER, required = false) String token) {
        if (!settings.accepts(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).cacheControl(CacheControl.noStore()).build();
        }
        return ResponseEntity.ok()
                .contentType(JSON_UTF8)
                .cacheControl(CacheControl.noStore())
                .body(service.json().getBytes(StandardCharsets.UTF_8));
    }
}
