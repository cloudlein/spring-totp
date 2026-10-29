package com.java.springtotp.controller;

import com.java.springtotp.dto.ApiResponse;
import com.java.springtotp.dto.PaginationResponse;
import com.java.springtotp.dto.user.UserCreateRequest;
import com.java.springtotp.dto.user.UserResponse;
import com.java.springtotp.dto.user.UserUpdateRequest;
import com.java.springtotp.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    public ApiResponse<Void>createUser(@Valid @RequestBody UserCreateRequest request) {
        userService.create(request);
        return ApiResponse.success("user created successfully", null);
    }

    @PatchMapping("/{id}")
    public ApiResponse<Void> updateUser(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
        userService.update(id, request);
        return ApiResponse.success("user updated successfully", null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteUser(@PathVariable Long id) {
        userService.delete(id);
        return ApiResponse.success("user deleted successfully", null);
    }

    @GetMapping
    public ApiResponse<PaginationResponse<UserResponse>> getAllUser(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 10, page = 0) Pageable pageable
    ) {
        Page<UserResponse> userResponsePage = userService.getAll(search, pageable);
        return ApiResponse.success("User retrieved successfully", PaginationResponse.from(userResponsePage));
    }

    @GetMapping("/{id}")
    public ApiResponse<UserResponse> getUserById(@PathVariable Long id) {
        userService.getById(id);
        return ApiResponse.success("user retrieved successfully", userService.getById(id));
    }


}
