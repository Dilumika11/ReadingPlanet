package com.epms.dto.response;

import com.epms.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private String token;
    private Long userId;
    private String fullName;
    private String email;
    private Role role;

    /** Same as role, as a list (the frontends read either). */
    public java.util.List<String> getRoles() {
        return role == null ? java.util.List.of() : java.util.List.of(role.name());
    }

    public static LoginResponse of(String token, com.epms.entity.User u) {
        return new LoginResponse(token, u.getUserId(), u.getFullName(), u.getEmail(), u.getRole());
    }
}