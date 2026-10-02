package com.lanparty.dashboard.admin;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminRepository extends JpaRepository<Admin, Long> {

    List<Admin> findByEnabledTrue();

    boolean existsByNameIgnoreCase(String name);

    List<Admin> findAllByOrderByName();
}
