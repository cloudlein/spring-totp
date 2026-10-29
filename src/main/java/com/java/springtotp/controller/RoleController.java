package com.java.springtotp.controller;

import com.java.springtotp.dto.ApiResponse;
import com.java.springtotp.dto.PaginationResponse;
import com.java.springtotp.dto.role.RoleCreateRequest;
import com.java.springtotp.dto.role.RoleResponse;
import com.java.springtotp.dto.role.RoleUpdateRequest;
import com.java.springtotp.service.RoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/role")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @PostMapping
    public ApiResponse<Void> createRole(@Valid @RequestBody RoleCreateRequest request) {
        roleService.create(request);
        return ApiResponse.success("Role created successfully", null);
    }

    @PatchMapping("/{id}")
    public ApiResponse<Void> updateRole(
            @PathVariable Long id,
            @Valid @RequestBody RoleUpdateRequest request) {
        roleService.update(id, request);
        return ApiResponse.success("Role updated successfully", null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteRole(@PathVariable Long id) {
        roleService.delete(id);
        return ApiResponse.success("Role deleted successfully", null);
    }

    @GetMapping
    public ApiResponse<PaginationResponse<RoleResponse>> getAllRoles(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 10, page = 0) Pageable pageable
    ) {
        Page<RoleResponse> roleResponsePage = roleService.getAll(search, pageable);
        return ApiResponse.success("Roles retrieved successfully", PaginationResponse.from(roleResponsePage));
    }

    @GetMapping("/{id}")
    public ApiResponse<RoleResponse> getRoleById(@PathVariable Long id) {
        return ApiResponse.success("Role retrieved successfully",  roleService.getById(id));
    }

}
