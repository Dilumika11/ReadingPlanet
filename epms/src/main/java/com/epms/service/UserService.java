package com.epms.service;

import com.epms.dto.request.LoginRequest;
import com.epms.dto.request.RegisterRequest;
import com.epms.dto.response.ApiResponse;

public interface UserService {

    ApiResponse<?> register(RegisterRequest request);

    ApiResponse<?> login(LoginRequest request);

    /** Admin only: creates an account with any role (staff accounts). */
    ApiResponse<?> createUser(RegisterRequest request);
}