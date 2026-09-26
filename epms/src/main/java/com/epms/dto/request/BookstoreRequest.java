package com.epms.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class BookstoreRequest {

    @NotBlank(message = "Bookstore name is required")
    @Size(max = 150)
    private String bookstoreName;

    @Size(max = 150)
    private String contactPerson;

    @Email(message = "Email is not valid")
    @Size(max = 100)
    private String email;

    @Pattern(regexp = "^$|^[+0-9 ()-]{7,20}$", message = "Phone number is not valid")
    private String phoneNumber;

    @Size(max = 255)
    private String address;

    @Size(max = 100)
    private String city;

    @Size(max = 100)
    private String country;
}
