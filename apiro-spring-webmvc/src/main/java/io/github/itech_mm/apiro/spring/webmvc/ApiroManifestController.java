package io.github.itech_mm.apiro.spring.webmvc;

import io.github.itech_mm.apiro.spec.ManifestVersion;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class ApiroManifestController {

    @GetMapping(value = "/apiro/manifest", produces = "application/json")
    @ResponseBody
    public String manifest() {
        return "{\"manifestVersion\":" + ManifestVersion.CURRENT + ",\"endpoints\":[]}";
    }
}