package com.ecommerce.userservice.service.impl;

import com.ecommerce.userservice.dto.request.LoginRequest;
import com.ecommerce.userservice.dto.request.RegisterRequest;
import com.ecommerce.userservice.dto.response.LoginResponse;
import com.ecommerce.userservice.dto.response.RegisterResponse;
import com.ecommerce.userservice.entity.AccountStatus;
import com.ecommerce.userservice.entity.Role;
import com.ecommerce.userservice.entity.User;
import com.ecommerce.userservice.exception.AccountNotActiveException;
import com.ecommerce.userservice.exception.InvalidCredentialsException;
import com.ecommerce.userservice.exception.UserAlreadyExistsException;
import com.ecommerce.userservice.mapper.UserMapper;
import com.ecommerce.userservice.repository.UserRepository;
import com.ecommerce.userservice.security.JwtService;
import com.ecommerce.userservice.service.AuthService;
import com.ecommerce.userservice.util.EmailUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
public class AuthServiceImpl implements AuthService {

    private static final String TOKEN_TYPE = "Bearer";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserMapper userMapper;

    /**
     * Hash compared against when the email doesn't exist, so "unknown email" and "wrong password"
     * take the same time and can't be told apart by response timing.
     */
    private final String dummyPasswordHash;

    public AuthServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           JwtService jwtService,
                           UserMapper userMapper) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.userMapper = userMapper;
        this.dummyPasswordHash = passwordEncoder.encode("timing-attack-mitigation-dummy");
    }

    @Override
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String email = EmailUtils.normalize(request.email());

        if (userRepository.existsByEmail(email)) {
            throw new UserAlreadyExistsException();
        }

        User user = User.builder()
                .firstName(request.firstName().trim())
                .lastName(request.lastName().trim())
                .email(email)
                .phoneNumber(request.phoneNumber().trim())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(Role.CUSTOMER)
                .accountStatus(AccountStatus.ACTIVE)
                .build();

        User saved;
        try {
            // saveAndFlush so a concurrent duplicate hits the unique constraint here, not at commit time
            saved = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw new UserAlreadyExistsException();
        }

        log.info("Registered new customer userId={}", saved.getId());
        return userMapper.toRegisterResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String email = EmailUtils.normalize(request.email());
        Optional<User> found = userRepository.findByEmail(email);

        if (found.isEmpty()) {
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            throw new InvalidCredentialsException();
        }

        User user = found.get();
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.info("Failed login attempt for userId={}", user.getId());
            throw new InvalidCredentialsException();
        }

        // Status is checked only after the password is verified, so it can't be probed without credentials
        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException(user.getAccountStatus());
        }

        String token = jwtService.generateAccessToken(user);
        log.info("Successful login for userId={}", user.getId());
        return new LoginResponse(token, TOKEN_TYPE, jwtService.getExpirationSeconds(), userMapper.toSummary(user));
    }
}
