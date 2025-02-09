package com.example.identityservice.service;

import com.example.identityservice.dto.request.AuthenticationRequest;
import com.example.identityservice.dto.request.IntrospectRequest;
import com.example.identityservice.dto.request.RefreshRequest;
import com.example.identityservice.dto.response.AuthenticationResponse;
import com.example.identityservice.dto.response.IntrospectResponse;
import com.example.identityservice.entity.User;
import com.example.identityservice.exception.AppException;
import com.example.identityservice.exception.ErrorCode;
import com.example.identityservice.repository.UserRepository;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthenticationService {
    UserRepository userRepository;
    PasswordEncoder passwordEncoder;
    RedisTemplate<String, String> redisTemplate;

    @NonFinal
    @Value("${jwt.signerKey}")
    protected String SIGNER_KEY;

    private static final long ACCESS_TOKEN_EXPIRY = 15 * 60 * 1000;
    private static final long REFRESH_TOKEN_EXPIRY = 7 * 24 * 60 * 60 * 1000;

    public IntrospectResponse introspect(IntrospectRequest request) throws JOSEException, ParseException {
        var token = request.getToken();
        SignedJWT signedJWT = parseAndVerifyToken(token);

        return IntrospectResponse.builder()
                .valid(!isTokenExpired(signedJWT))
                .build();
    }

    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        var user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }

        var accessToken = generateAccessToken(user);
        var refreshToken = generateRefreshToken(user);
        redisTemplate.opsForValue().set("refresh:" + user.getUsername(), refreshToken, REFRESH_TOKEN_EXPIRY, TimeUnit.MILLISECONDS);

        return AuthenticationResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .authenticated(true)
                .build();
    }

    public AuthenticationResponse refreshToken(RefreshRequest request) {
        try {
            SignedJWT signedJWT = parseAndVerifyToken(request.getRefreshToken());

            if (isTokenExpired(signedJWT)) {
                throw new AppException(ErrorCode.UNAUTHENTICATED);
            }

            String username = getUsernameFromToken(signedJWT);

            if (!isRefreshTokenValid(username, request.getRefreshToken())) {
                throw new AppException(ErrorCode.UNAUTHENTICATED);
            }

            var user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

            var newAccessToken = generateAccessToken(user);

            return AuthenticationResponse.builder()
                    .accessToken(newAccessToken)
                    .refreshToken(request.getRefreshToken())
                    .authenticated(true)
                    .build();
        } catch (Exception e) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
    }

    public void logout(RefreshRequest request) {
        try {
            SignedJWT signedJWT = parseAndVerifyToken(request.getRefreshToken());
            String username = getUsernameFromToken(signedJWT);

            if (!isRefreshTokenValid(username, request.getRefreshToken())) {
                throw new AppException(ErrorCode.UNAUTHENTICATED);
            }

            redisTemplate.delete("refresh:" + username);
        } catch (Exception e) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
    }

    private String generateAccessToken(User user) {
        return generateToken(user, ACCESS_TOKEN_EXPIRY, true);
    }

    private String generateRefreshToken(User user) {
        return generateToken(user, REFRESH_TOKEN_EXPIRY, false);
    }

    // 1. By default, OAuth2 systems store role information in the "scope" claim within the JWT payload.
    //    - The "scope" claim contains a space-separated list of roles assigned to the user.
    //    - When decoding the token, Spring Security extracts these roles and maps them into the user's granted authorities.
    //    - However, by default, Spring Security prefixes each extracted role with "SCOPE_".
    //      For example, if the JWT contains:
    //      { "scope": "USER ADMIN" }
    //      The granted authorities will be:
    //      ["SCOPE_USER", "SCOPE_ADMIN"].
    //    - This default behavior is intended to prevent conflicts with other authority types.
    private String generateToken(User user, long expiryMillis, boolean isAccessToken) {
        try {
            JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                    .subject(user.getUsername())
                    .issuer("identity-service")
                    .issueTime(new Date())
                    .expirationTime(new Date(System.currentTimeMillis() + expiryMillis));

            if (isAccessToken) {
                claimsBuilder.claim("scope", buildScope(user));
            }

            JWTClaimsSet claims = claimsBuilder.build();
            SignedJWT signedJWT = new SignedJWT(new JWSHeader(JWSAlgorithm.HS512), claims);
            signedJWT.sign(new MACSigner(SIGNER_KEY.getBytes()));

            return signedJWT.serialize();
        } catch (JOSEException e) {
            log.error("Cannot create token", e);
            throw new RuntimeException(e);
        }
    }


    private String buildScope(User user) {
        StringJoiner scopes = new StringJoiner(" ");
        user.getRoles().forEach(role -> scopes.add(role.getName()));
        return scopes.toString();
    }

    private SignedJWT parseAndVerifyToken(String token) {
        try {
            JWSVerifier verifier = new MACVerifier(SIGNER_KEY.getBytes());
            SignedJWT signedJWT = SignedJWT.parse(token);

            if (!signedJWT.verify(verifier)) {
                throw new AppException(ErrorCode.UNAUTHENTICATED);
            }

            return signedJWT;
        } catch (Exception e) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
    }

    private boolean isTokenExpired(SignedJWT signedJWT) throws ParseException {
        return signedJWT.getJWTClaimsSet().getExpirationTime().before(new Date());
    }

    private String getUsernameFromToken(SignedJWT signedJWT) throws ParseException {
        return signedJWT.getJWTClaimsSet().getSubject();
    }

    private boolean isRefreshTokenValid(String username, String refreshToken) {
        String storedToken = redisTemplate.opsForValue().get("refresh:" + username);
        return storedToken != null && storedToken.equals(refreshToken);
    }

}
