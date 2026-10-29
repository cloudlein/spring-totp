package com.java.springtotp.mapper;

import com.java.springtotp.dto.auth.RegisterRequest;
import com.java.springtotp.dto.user.UserCreateRequest;
import com.java.springtotp.dto.user.UserResponse;
import com.java.springtotp.model.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    User toEntity(UserCreateRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "role", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    User toEntity(RegisterRequest request);

    @Mapping(source = "role.id", target = "roleId")
    @Mapping(source = "role.role", target = "role")
    UserResponse toResponse(User user);
}
