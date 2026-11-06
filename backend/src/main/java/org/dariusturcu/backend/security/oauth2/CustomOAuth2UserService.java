package org.dariusturcu.backend.security.oauth2;

import org.dariusturcu.backend.model.user.AuthProvider;
import org.dariusturcu.backend.model.user.Role;
import org.dariusturcu.backend.model.user.User;
import org.dariusturcu.backend.repository.UserRepository;
import org.dariusturcu.backend.util.EmailAddresses;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {
    public static final String ACCOUNT_EXISTS_ERROR_CODE = "account_exists";
    public static final String EMAIL_NOT_VERIFIED_ERROR_CODE = "email_not_verified";
    private static final String ACCOUNT_EXISTS_MESSAGE =
            "An account with this email already exists, log in with your password instead";

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

        if (!oAuth2UserInfo.isEmailVerified()) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error(EMAIL_NOT_VERIFIED_ERROR_CODE),
                    "The email address of this Google account is not verified"
            );
        }
        String email = EmailAddresses.normalize(oAuth2UserInfo.getEmail());

        Optional<User> userOptional = userRepository.findUserByAuthProviderAndAuthProviderId(
                provider,
                oAuth2UserInfo.getId()
        );

        if (userOptional.isPresent()) {
            User user = userOptional.get();
            boolean emailTakenByAnotherAccount = !email.equalsIgnoreCase(user.getEmail())
                    && userRepository.existsUserByEmail(email);
            if (!emailTakenByAnotherAccount) {
                user.setEmail(email);
            }
            if (!user.getUsername().equalsIgnoreCase(oAuth2UserInfo.getName())) {
                user.setUsername(resolveUniqueUsername(oAuth2UserInfo.getName()));
            }
            user.setImageUrl(oAuth2UserInfo.getImageUrl());
            // Google re-confirms this email on every login, so an existing account is kept
            // verified even if it somehow predates this flag.
            user.setEmailVerified(true);
            return userRepository.save(user);
        }

        if (userRepository.existsUserByEmail(email)) {
            throw accountExists();
        }

        User user = new User();
        user.setAuthProvider(provider);
        user.setAuthProviderId(oAuth2UserInfo.getId());
        user.setUsername(resolveUniqueUsername(oAuth2UserInfo.getName()));
        user.setEmail(email);
        user.setImageUrl(oAuth2UserInfo.getImageUrl());
        user.setRole(Role.USER);
        // Google has already verified this email; a local signup is the only one that
        // starts unverified.
        user.setEmailVerified(true);

        try {
            return userRepository.save(user);
        } catch (DataIntegrityViolationException duplicateAccountException) {
            if (userRepository.existsUserByEmail(email)) {
                throw accountExists();
            }
            throw duplicateAccountException;
        }
    }

    private OAuth2AuthenticationException accountExists() {
        return new OAuth2AuthenticationException(new OAuth2Error(ACCOUNT_EXISTS_ERROR_CODE), ACCOUNT_EXISTS_MESSAGE);
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
