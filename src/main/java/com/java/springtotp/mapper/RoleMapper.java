package com.java.springtotp.mapper;

import com.java.springtotp.dto.role.RoleCreateRequest;
import com.java.springtotp.dto.role.RoleResponse;
import com.java.springtotp.model.Role;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface RoleMapper {


    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(source = "roleName", target = "role")
    Role toEntity(RoleCreateRequest request);

    @Mapping(source = "role.role", target = "role")
    RoleResponse toResponse(Role role);

}
