package com.lanparty.dashboard.user;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByNicknameIgnoreCase(String nickname);

    Optional<AppUser> findByEmailIgnoreCase(String email);

    boolean existsByNicknameIgnoreCase(String nickname);

    boolean existsByEmailIgnoreCase(String email);

    long countByRoleAndEnabledTrue(UserRole role);

    List<AppUser> findAllByOrderByNicknameAsc();

    List<AppUser> findByIdIn(Collection<Long> ids);
}
