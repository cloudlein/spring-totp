package com.java.springtotp.service.impl;

import com.java.springtotp.dto.ApiResponse;
import com.java.springtotp.dto.role.RoleCreateRequest;
import com.java.springtotp.dto.role.RoleResponse;
import com.java.springtotp.dto.role.RoleUpdateRequest;
import com.java.springtotp.exception.ResourceAlreadyExistsException;
import com.java.springtotp.exception.ResourceNotFoundException;
import com.java.springtotp.mapper.RoleMapper;
import com.java.springtotp.model.Role;
import com.java.springtotp.repository.RoleRepository;
import com.java.springtotp.service.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;
    private final RoleMapper roleMapper;

    @PreAuthorize("hasAuthority('ROLE_WRITE')")
    @Transactional
    @Override
    public void create(RoleCreateRequest request) {
        if (checkIfRoleAlreadyExists(request.getRoleName())) {
            throw new ResourceAlreadyExistsException("Role already exists");
        }

        Role role = roleMapper.toEntity(request);
        roleRepository.save(role);
    }

    @PreAuthorize("hasAuthority('ROLE_WRITE')")
    @Transactional
    @Override
    public void update(Long id, RoleUpdateRequest request) {
        Role role = checkIfRoleExists(id);


        if (request.getRoleName() != null && !request.getRoleName().equals(role.getRole())) {
            if (checkIfRoleAlreadyExists(request.getRoleName())) {
                throw new ResourceAlreadyExistsException("Role already in use");
            }
            role.setRole(request.getRoleName());
        }


        roleRepository.save(role);

    }

    @PreAuthorize("hasAuthority('ROLE_DELETE')")
    @Transactional
    @Override
    public void delete(Long id) {
        Role role = checkIfRoleExists(id);
        roleRepository.delete(role);
    }

    @PreAuthorize("hasAuthority('ROLE_READ')")
    @Transactional(readOnly = true)
    @Override
    public RoleResponse getById(Long id) {
       Role role = checkIfRoleExists(id);
       return roleMapper.toResponse(role);
    }

    @PreAuthorize("hasAuthority('ROLE_READ')")
    @Transactional(readOnly = true)
    @Override
    public Page<RoleResponse> getAll(String search, Pageable pageable) {
       Page<Role> rolePage;
       if (search != null && !search.trim().isEmpty()) {
           rolePage = roleRepository.getAllByRoleName(search.trim(), pageable);
       }else {
           rolePage = roleRepository.findAll(pageable);
       }

       return rolePage.map(roleMapper::toResponse);
    }


    private Boolean checkIfRoleAlreadyExists(String roleName) {
        return roleRepository.existsByRole(roleName);
    }

    private Role checkIfRoleExists(Long roleId) {
        return roleRepository.findById(roleId).orElseThrow(() -> new ResourceNotFoundException("Role not found"));
    }

}
