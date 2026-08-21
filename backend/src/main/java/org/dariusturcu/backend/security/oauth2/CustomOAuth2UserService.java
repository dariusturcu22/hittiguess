package org.dariusturcu.backend.security.oauth2;

import org.dariusturcu.backend.model.user.AuthProvider;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.UserRepository;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {
    private final UserRepository userRepository;

    public CustomOAuth2UserService(UserRepository userRepository) {
        super();
        this.userRepository = userRepository;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        OAuth2UserInfo oAuth2UserInfo = new GoogleOAuth2UserInfo(oAuth2User.getAttributes());

        User user = processOAuth2User(registrationId, oAuth2UserInfo);

        return new CustomOAuth2User(user, oAuth2User.getAttributes());
    }

    private User processOAuth2User(String registrationId, OAuth2UserInfo oAuth2UserInfo) {
        AuthProvider provider = AuthProvider.valueOf(registrationId.toUpperCase());

        Optional<User> userOptional = userRepository.findUserByAuthProviderAndAuthProviderId(
                provider,
                oAuth2UserInfo.getId()
        );

        if (userOptional.isPresent()) {
            User user = userOptional.get();
            user.setEmail(oAuth2UserInfo.getEmail());
            if (!user.getUsername().equals(oAuth2UserInfo.getName())) {
                user.setUsername(resolveUniqueUsername(oAuth2UserInfo.getName()));
            }
            user.setImageUrl(oAuth2UserInfo.getImageUrl());
            return userRepository.save(user);
        }

        if (userRepository.existsUserByEmail(oAuth2UserInfo.getEmail())) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("account_exists"),
                    "An account with this email already exists, log in with your password instead"
            );
        }

        User user = new User();
        user.setAuthProvider(provider);
        user.setAuthProviderId(oAuth2UserInfo.getId());
        user.setUsername(resolveUniqueUsername(oAuth2UserInfo.getName()));
        user.setEmail(oAuth2UserInfo.getEmail());
        user.setImageUrl(oAuth2UserInfo.getImageUrl());
        user.setRole(Role.USER);

        return userRepository.save(user);
    }

    private String resolveUniqueUsername(String desired) {
        if (!userRepository.existsUserByUsername(desired)) {
            return desired;
        }
        String candidate;
        int suffix = 1;
        do {
            candidate = desired + suffix;
            suffix++;
        } while (userRepository.existsUserByUsername(candidate));
        return candidate;
    }
}
