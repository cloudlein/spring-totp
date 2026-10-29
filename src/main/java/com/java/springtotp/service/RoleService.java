package com.java.springtotp.service;

import com.java.springtotp.dto.ApiResponse;
import com.java.springtotp.dto.PaginationResponse;
import com.java.springtotp.dto.role.RoleCreateRequest;
import com.java.springtotp.dto.role.RoleResponse;
import com.java.springtotp.dto.role.RoleUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface RoleService {

    void create(RoleCreateRequest request);
    void update(Long id, RoleUpdateRequest request);
    void delete(Long id);
    RoleResponse getById(Long id);
    Page<RoleResponse> getAll(String search, Pageable pageable);

}
