package com.stripedape.dashboard.controller;

import com.stripedape.dashboard.config.AppProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Principal;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class ViewModelAdvice {

    private final AppProperties properties;

    public ViewModelAdvice(AppProperties properties) {
        this.properties = properties;
    }

    @ModelAttribute("appName")
    public String appName() {
        return properties.getName();
    }

    @ModelAttribute("whatsappLink")
    public String whatsappLink() {
        return "https://wa.me/27696935713";
    }

    @ModelAttribute("whatsappLabel")
    public String whatsappLabel() {
        return "stripedApe";
    }

    @ModelAttribute("currentPrincipal")
    public String currentPrincipal(Principal principal) {
        return principal == null ? "" : principal.getName();
    }

    @ModelAttribute("brandLogoUrl")
    public String brandLogoUrl() {
        String logoPath = properties.getBranding().getLogoPath();
        if (logoPath == null || logoPath.isBlank()) {
            return null;
        }
        Path path = Paths.get(logoPath);
        return Files.exists(path) ? "/branding/logo" : null;
    }

    @ModelAttribute("currentPath")
    public String currentPath(HttpServletRequest request) {
        return request == null ? "" : request.getRequestURI();
    }
}
