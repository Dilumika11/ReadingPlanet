package com.epms.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;

@Data
public class AuthorProfileRequest {

    @NotBlank(message = "First name is required")
    @Size(max = 100)
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(max = 100)
    private String lastName;

    @Pattern(regexp = "^$|^[+0-9 ()-]{7,20}$", message = "Phone number is not valid")
    private String phoneNumber;

    @Size(max = 150, message = "Pen name must be 150 characters or fewer")
    private String penName;

    @Size(max = 5000, message = "Biography must be 5000 characters or fewer")
    private String biography;

    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;

    @Size(max = 100)
    private String nationality;

    @Pattern(regexp = "^$|^https?://\\S{3,250}$", message = "Website must start with http:// or https://")
    private String website;
}
