package com.ecommerce.userservice.config;

import com.ecommerce.userservice.entity.AccountStatus;
import com.ecommerce.userservice.entity.Role;
import com.ecommerce.userservice.entity.User;
import com.ecommerce.userservice.repository.UserRepository;
import com.ecommerce.userservice.util.EmailUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Registration only creates CUSTOMER accounts, so the first ADMIN is created here on startup
 * from ADMIN_EMAIL / ADMIN_PASSWORD. It does nothing if they are unset or the email already exists,
 * so it is safe to leave enabled across restarts.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrapRunner implements ApplicationRunner {

    private final AdminBootstrapProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(properties.email()) || !StringUtils.hasText(properties.password())) {
            log.info("Admin bootstrap skipped: ADMIN_EMAIL / ADMIN_PASSWORD not set");
            return;
        }

        String email = EmailUtils.normalize(properties.email());
        if (userRepository.existsByEmail(email)) {
            log.info("Admin bootstrap skipped: an account with the configured admin email already exists");
            return;
        }

        User admin = User.builder()
                .firstName(properties.firstName())
                .lastName(properties.lastName())
                .email(email)
                .phoneNumber(properties.phoneNumber())
                .passwordHash(passwordEncoder.encode(properties.password()))
                .role(Role.ADMIN)
                .accountStatus(AccountStatus.ACTIVE)
                .build();
        userRepository.save(admin);

        log.info("Bootstrap admin account created for {}", email);
    }
}
