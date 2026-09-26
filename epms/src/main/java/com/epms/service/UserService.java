package com.epms.service;

import com.epms.dto.request.LoginRequest;
import com.epms.dto.request.RegisterRequest;
import com.epms.dto.response.ApiResponse;

public interface UserService {

    ApiResponse<?> register(RegisterRequest request);

    /** Which login page is used: STAFF rejects authors/customers, STORE rejects staff. */
    enum LoginPortal { STAFF, STORE }

    ApiResponse<?> login(LoginRequest request);

    ApiResponse<?> login(LoginRequest request, LoginPortal portal);

    /** Admin only: creates an account with any role (staff accounts). */
    ApiResponse<?> createUser(RegisterRequest request);
}