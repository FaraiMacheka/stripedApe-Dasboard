package com.stripedape.dashboard.controller;

import com.stripedape.dashboard.config.AppProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class BrandingController {

    private final AppProperties properties;

    public BrandingController(AppProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/branding/logo")
    public ResponseEntity<Resource> logo() throws IOException {
        String logoPath = properties.getBranding().getLogoPath();
        if (!StringUtils.hasText(logoPath)) {
            return ResponseEntity.notFound().build();
        }

        Path path = Paths.get(logoPath);
        if (!Files.exists(path)) {
            return ResponseEntity.notFound().build();
        }

        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        String detectedType = Files.probeContentType(path);
        if (StringUtils.hasText(detectedType)) {
            mediaType = MediaType.parseMediaType(detectedType);
        }

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .contentType(mediaType)
                .body(new FileSystemResource(path));
    }
}
