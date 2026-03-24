package com.stripedape.dashboard.controller;

import com.stripedape.dashboard.domain.AppRole;
import com.stripedape.dashboard.service.UserAdminService;
import com.stripedape.dashboard.service.UserForm;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserAdminService userAdminService;

    public UserController(UserAdminService userAdminService) {
        this.userAdminService = userAdminService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("users", userAdminService.listUsers());
        return "users";
    }

    @GetMapping("/new")
    public String create(Model model) {
        model.addAttribute("form", new UserForm());
        model.addAttribute("roles", AppRole.values());
        return "user-form";
    }

    @GetMapping("/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        var user = userAdminService.getRequired(id);
        UserForm form = new UserForm();
        form.setId(user.getId());
        form.setUsername(user.getUsername());
        form.setDisplayName(user.getDisplayName());
        form.setEnabled(user.isEnabled());
        form.setRoles(user.getRoles());
        model.addAttribute("form", form);
        model.addAttribute("roles", AppRole.values());
        return "user-form";
    }

    @PostMapping
    public String save(@ModelAttribute("form") UserForm form) {
        userAdminService.save(form);
        return "redirect:/users";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        userAdminService.delete(id);
        return "redirect:/users";
    }
}
