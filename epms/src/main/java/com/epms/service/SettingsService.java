package com.epms.service;

import com.epms.dto.request.SettingUpdateRequest;
import com.epms.entity.SystemSetting;

import java.util.List;

public interface SettingsService {

    List<SystemSetting> getAll();

    /**
     * Creates or updates the setting by key. Per spec: settings changes
     * apply to future calculations only, never retroactively.
     */
    SystemSetting upsert(SettingUpdateRequest request, Long updatedBy);
}
