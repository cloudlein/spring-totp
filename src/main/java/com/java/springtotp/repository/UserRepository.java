package com.java.springtotp.repository;

import com.java.springtotp.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User,Long> {

    Boolean existsByName(String name);
    Boolean existsByUsername(String username);
    Boolean existsByEmail(String email);

    Optional<User> findByUsername(String username);

    @Query(
            "SELECT u FROM User u WHERE " +
                    "LOWER(u.name) LIKE(CONCAT('%', :keyword, '%')) OR " +
                    "LOWER(u.username) LIKE(CONCAT('%', :keyword, '%')) OR " +
                    "LOWER(u.email) LIKE(CONCAT('%', :keyword, '%')) "
    )
    Page<User> getAllUserByNameUsernameEmail(@Param("keyword") String keyword, Pageable pageable);
}
