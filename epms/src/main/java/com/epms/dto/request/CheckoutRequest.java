package com.epms.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CheckoutRequest {

    @NotBlank(message = "Recipient name is required")
    @Size(max = 150)
    private String recipientName;

    @NotBlank(message = "Recipient phone number is required")
    @Pattern(regexp = "^[+0-9 ()-]{7,20}$", message = "Phone number is not valid")
    private String recipientPhone;

    @NotBlank(message = "Delivery address is required")
    @Size(max = 255, message = "Address must be 255 characters or fewer")
    private String shippingAddress;
}
