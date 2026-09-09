package com.epms.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SettingUpdateRequest {

    @NotBlank(message = "Setting key is required")
    private String settingKey;

    private String settingValue;

    private String description;
}
