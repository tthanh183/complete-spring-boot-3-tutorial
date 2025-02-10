package com.example.identityservice.dto.response;

import com.example.identityservice.entity.Role;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserResponse {
    String id;
    String response;
    String username;
    String password;
    String firstName;
    String lastName;
    LocalDate dob;
    Set<RoleResponse> roles;
}
