package com.epms.service.impl;

import com.epms.dto.request.SettingUpdateRequest;
import com.epms.entity.SystemSetting;
import com.epms.repository.SystemSettingRepository;
import com.epms.service.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SettingsServiceImpl implements SettingsService {

    private final SystemSettingRepository settingRepository;

    @Override
    public List<SystemSetting> getAll() {
        return settingRepository.findAll();
    }

    @Override
    public SystemSetting upsert(SettingUpdateRequest request, Long updatedBy) {

        SystemSetting setting = settingRepository.findBySettingKey(request.getSettingKey())
                .orElseGet(SystemSetting::new);

        setting.setSettingKey(request.getSettingKey());
        setting.setSettingValue(request.getSettingValue());
        setting.setDescription(request.getDescription());
        setting.setUpdatedBy(updatedBy);

        return settingRepository.save(setting);
    }
}
