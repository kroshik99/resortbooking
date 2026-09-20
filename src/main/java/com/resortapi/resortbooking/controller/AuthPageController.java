package com.resortapi.resortbooking.controller;

import com.resortapi.resortbooking.dto.RegisterForm;
import com.resortapi.resortbooking.dto.RegisterRequest;
import com.resortapi.resortbooking.exception.DuplicateResourceException;
import com.resortapi.resortbooking.service.AuthService;

import jakarta.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthPageController {

    private final AuthService authService;

    public AuthPageController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/login")
    public String login() {
        return "auth/login";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("registerForm", new RegisterForm());
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("registerForm") RegisterForm form,
                           BindingResult result,
                           RedirectAttributes flash) {
        if (result.hasErrors()) {
            return "auth/register";
        }
        try {
            authService.register(new RegisterRequest(
                    form.getFullName(), form.getEmail(), form.getPassword(), form.getPhone()));
        } catch (DuplicateResourceException e) {
            result.rejectValue("email", "duplicate", e.getMessage());
            return "auth/register";
        }

        flash.addFlashAttribute("success", "Account created. Please log in.");
        return "redirect:/login";
    }
}
