package com.epms.config;

import com.epms.entity.RoleEntity;
import com.epms.enums.Role;
import com.epms.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RoleDataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;

    @Override
    public void run(String... args) {

        for (Role role : Role.values()) {

            if (!roleRepository.existsByRoleName(role.name())) {

                RoleEntity roleEntity = new RoleEntity();

                roleEntity.setRoleName(role.name());
                roleEntity.setDescription(
                        role.name().replace("_", " ") + " role"
                );

                roleRepository.save(roleEntity);
            }
        }
    }
}