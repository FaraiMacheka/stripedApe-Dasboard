package com.stripedape.dashboard.controller;

import com.stripedape.dashboard.config.AppProperties;
import java.security.Principal;
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
}
