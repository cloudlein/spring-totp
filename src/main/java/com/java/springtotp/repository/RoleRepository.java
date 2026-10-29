package com.java.springtotp.repository;

import com.java.springtotp.model.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role,Long> {

    Boolean existsByRole(String role);

    Optional<Role> findByRole(String role);

    @Query("SELECT r FROM Role r WHERE LOWER(r.role) LIKE(CONCAT('%', :keyword, '%'))")
    Page<Role> getAllByRoleName(@Param("keyword") String keyword, Pageable pageable);

}
