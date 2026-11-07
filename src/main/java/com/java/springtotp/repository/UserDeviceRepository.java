package com.java.springtotp.repository;

import com.java.springtotp.model.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserDeviceRepository extends JpaRepository<UserDevice, Long> {

    Optional<UserDevice> findByUserIdAndDeviceHash(Long userId, String deviceHash);

    List<UserDevice>  findAllByUserId(Long userId);



}
