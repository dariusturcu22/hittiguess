package org.dariusturcu.backend.model.mapper;

import org.dariusturcu.backend.model.user.AuthProvider;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.model.user.UserDetailDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UserMapperTest {

    private static final Long USER_ID = 7L;

    private final UserMapper userMapper = new UserMapper();

    @Test
    void detailCarriesTheAdminRole() {
        UserDetailDTO detail = userMapper.toDetailDTO(accountWithRole(Role.ADMIN), List.of());

        assertThat(detail.role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void detailCarriesTheUserRole() {
        UserDetailDTO detail = userMapper.toDetailDTO(accountWithRole(Role.USER), List.of());

        assertThat(detail.role()).isEqualTo(Role.USER);
    }

    private User accountWithRole(Role role) {
        User user = new User();
        user.setId(USER_ID);
        user.setUsername("account-username");
        user.setAuthProvider(AuthProvider.LOCAL);
        user.setRole(role);
        return user;
    }
}
