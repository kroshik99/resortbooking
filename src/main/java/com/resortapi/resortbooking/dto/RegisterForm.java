package com.resortapi.resortbooking.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Form-binding object for the register page: mutable with a no-arg constructor,
 * which is what Thymeleaf's th:field and Spring's data binder need.
 */
public class RegisterForm {

    @NotBlank(message = "Please enter your name")
    @Size(max = 100)
    private String fullName;

    @NotBlank(message = "Please enter your email")
    @Email(message = "That does not look like an email address")
    @Size(max = 150)
    private String email;

    @NotBlank(message = "Please choose a password")
    @Size(min = 8, max = 72, message = "Use at least 8 characters")
    private String password;

    @Size(max = 20)
    private String phone;

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}
