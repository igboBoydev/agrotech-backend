package com.catalyst.Home.Services.Impl;

import auth.Auth;
import com.catalyst.Home.Entity.FarmersDetails;
import com.catalyst.Home.Entity.TokenValidation;
import com.catalyst.Home.Entity.Users;
import com.catalyst.Home.Repositories.FarmerRepository;
import com.catalyst.Home.Repositories.TokenValidationRepository;
import com.catalyst.Home.Repositories.UsersRepository;
import com.catalyst.Home.Security.JwtService;
import com.catalyst.Home.Services.AuthService;
import com.catalyst.Home.Utils.ValidateType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UsersRepository usersRepository;
    private final TokenValidationRepository tokenValidationRepository;
    private final PasswordEncoder passwordEncoder;
    private final FarmerRepository farmerRepository;
    private final JwtService jwtService;

    @Value("${app.jwt.otp-expiration-ms}")
    private long otpExpirationMs;

    @Override
    public Auth.GenerateTokenResponse generateToken(Auth.GenerateTokenRequest request) {
        log.info("started generateToken ==================== ");
        String otp = String.valueOf(100000 + RANDOM.nextInt(900000));
        ZonedDateTime now = ZonedDateTime.now();

        tokenValidationRepository.save(TokenValidation.builder()
                .email(request.getEmail())
                .verificationCode(otp)
                .verificationType(ValidateType.EMAIL)
                .expiry(now.plus(Duration.ofMillis(otpExpirationMs)).toString())
                .createdAt(now.toString())
                .updatedAt(now.toString())
                .build());

        return Auth.GenerateTokenResponse.newBuilder()
                .setToken(otp)
                .setRefreshToken("")
                .build();
    }

    @Override
    @Transactional
    public Auth.TokenResponse validateToken(Auth.TokenRequest request) {
        ValidateType type = ValidateType.valueOf(request.getValidateType().name());

        Optional<TokenValidation> tokenOpt = tokenValidationRepository
                .findTopByVerificationCodeAndVerificationTypeAndUsedFalseOrderByIdDesc(request.getToken(), type);

        if (tokenOpt.isEmpty()) {
            return Auth.TokenResponse.newBuilder().setMessage("Invalid or already used code").setCode("400").build();
        }

        TokenValidation token = tokenOpt.get();
        if (ZonedDateTime.parse(token.getExpiry()).isBefore(ZonedDateTime.now())) {
            token.setExpired(true);
            tokenValidationRepository.save(token);
            return Auth.TokenResponse.newBuilder().setMessage("Code expired").setCode("400").build();
        }

        token.setUsed(true);
        tokenValidationRepository.save(token);
        return Auth.TokenResponse.newBuilder().setMessage("Verified").setCode("200").build();
    }

    @Override
    public Auth.ValidateEmailResponse validateEmail(Auth.ValidateEmailRequest request) {
        boolean taken = usersRepository.findByEmail(request.getEmail()).isPresent();
        return Auth.ValidateEmailResponse.newBuilder()
                .setMessage(taken ? "Email already registered" : "Email available")
                .setCode(taken ? "409" : "200")
                .build();
    }

    @Override
    public Auth.ValidatePhoneResponse validatePhone(Auth.ValidatePhoneRequest request) {
        boolean taken = usersRepository.findByPhoneNumber(request.getPhone()).isPresent();
        return Auth.ValidatePhoneResponse.newBuilder()
                .setMessage(taken ? "Phone number already registered" : "Phone number available")
                .setCode(taken ? "409" : "200")
                .build();
    }

    @Override
    public Auth.ChangeFarmerPasswordResponse changeFarmerPassword(Auth.ChangeFarmerPasswordRequest request) {
        return Auth.ChangeFarmerPasswordResponse.newBuilder()
                .setMessage("Not implemented: ChangeFarmerPasswordRequest carries no way to identify the farmer (no email/userId/session token)")
                .setCode("501")
                .build();
    }

    @Override
    public Auth.FarmerLoginResponse farmerLogin(Auth.FarmerLoginRequest request) {
        Optional<Users> userOpt = usersRepository.findByEmail(request.getEmail());

        if (userOpt.isEmpty() || !passwordEncoder.matches(request.getPassword(), userOpt.get().getPassword())) {
            return Auth.FarmerLoginResponse.newBuilder()
                    .setMessage("Invalid email or password")
                    .setCode("401")
                    .build();
        }

        Users user = userOpt.get();
        if (Boolean.FALSE.equals(user.getEnabled()) || Boolean.FALSE.equals(user.getAccountNonLocked())) {
            return Auth.FarmerLoginResponse.newBuilder()
                    .setMessage("Account disabled or locked")
                    .setCode("403")
                    .build();
        }

        Optional<FarmersDetails> customerByEmail = farmerRepository.findByEmail(request.getEmail());

        if (customerByEmail.isEmpty()) {
            return Auth.FarmerLoginResponse.newBuilder()
                    .setMessage("Account not found")
                    .setCode("403")
                    .build();
        }

        String token = jwtService.generateToken(user.getEmail(), Map.of(
                "userId", user.getId(),
                "role", user.getRole().name()
        ));

        return Auth.FarmerLoginResponse.newBuilder()
                .setToken(token)
                .setUuid(customerByEmail.get().getUuid())
                .setMessage("Login successful")
                .setCode("200")
                .build();
    }
}
