package org.dariusturcu.backend.repository;

import org.dariusturcu.backend.model.user.AuthProvider;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    @Query("select user from User user where lower(user.email) = lower(:email)")
    Optional<User> findUserByEmail(@Param("email") String email);

    @Query("select count(user) > 0 from User user where lower(user.username) = lower(:username)")
    Boolean existsUserByUsername(@Param("username") String username);

    @Query("select count(user) > 0 from User user where lower(user.email) = lower(:email)")
    Boolean existsUserByEmail(@Param("email") String email);

    Boolean existsUserByRole(Role role);

    Boolean existsUserByRoleAndEmailEndingWithIgnoreCase(Role role, String emailSuffix);

    Optional<User> findUserByAuthProviderAndAuthProviderId(AuthProvider authProvider, String authProviderId);

    List<User> findByEmailVerifiedFalseAndCreatedAtBefore(Instant cutoff);
}
