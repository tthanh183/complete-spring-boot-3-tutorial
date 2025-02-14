package com.example.identityservice.service;

import com.example.identityservice.dto.request.UserCreationRequest;
import com.example.identityservice.dto.response.UserResponse;
import com.example.identityservice.entity.User;
import com.example.identityservice.exception.AppException;
import com.example.identityservice.exception.ErrorCode;
import com.example.identityservice.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;

@SpringBootTest
public class UserServiceTest {
    @Autowired
    private UserService userService;

    @MockitoBean
    private UserRepository userRepository;

    private UserCreationRequest request;
    private UserResponse response;
    private User user;
    private LocalDate dob;

    @BeforeEach
    void initData() {
        dob = LocalDate.of(2005, 2, 1);
        request = UserCreationRequest.builder()
                .username("tomdoe")
                .password("12345678")
                .firstName("Tom")
                .lastName("Doe")
                .dob(dob)
                .build();

        response = UserResponse.builder()
                .id("1")
                .username("tomdoe")
                .firstName("Tom")
                .lastName("Doe")
                .dob(dob)
                .build();

        user = User.builder()
                .id("1")
                .username("tomdoe")
                .firstName("Tom")
                .lastName("Doe")
                .dob(dob)
                .build();
    }

    @Test
    void createUser_validRequest_success() {
        // Given
        Mockito.when(userRepository.existsByUsername(anyString())).thenReturn(false);
        Mockito.when(userRepository.save(Mockito.any())).thenReturn(user);

        // When
        UserResponse actual = userService.createUser(request);

        // Then
        assertThat(actual.getId()).isEqualTo(user.getId());
        assertThat(actual.getUsername()).isEqualTo(user.getUsername());
        assertThat(actual.getFirstName()).isEqualTo(user.getFirstName());
        assertThat(actual.getLastName()).isEqualTo(user.getLastName());
    }

    @Test
    void createUser_userExisted_fail() {
        // Given
        Mockito.when(userRepository.existsByUsername(anyString())).thenReturn(true);

        // When, Then
        var exception = assertThrows(AppException.class, () -> userService.createUser(request));
        assertThat(exception.getErrorCode().getCode()).isEqualTo(ErrorCode.USER_EXISTED.getCode());
    }

    @Test
    @WithMockUser(username = "tomdoe")
    void getMyInfo_valid_success() {
        // Given
        Mockito.when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(user));

        // When
        UserResponse actual = userService.getMyInfo();

        // Then
        assertThat(actual.getId()).isEqualTo(user.getId());
        assertThat(actual.getUsername()).isEqualTo(user.getUsername());
        assertThat(actual.getFirstName()).isEqualTo(user.getFirstName());
        assertThat(actual.getLastName()).isEqualTo(user.getLastName());
    }

    @Test
    @WithMockUser(username = "tomdoe")
    void getMyInfo_userNotFound_fail() {
        // Given
        Mockito.when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());

        // When, Then
        var exception = assertThrows(AppException.class, () -> userService.getMyInfo());

        assertThat(exception.getErrorCode().getCode()).isEqualTo(ErrorCode.USER_NOT_EXISTED.getCode());
    }
}
