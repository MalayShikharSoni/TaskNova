package com.tasknova.controller.api;

import com.tasknova.dto.user.UserResponseDto;
import com.tasknova.dto.user.UserUpdateDto;
import com.tasknova.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * Self-service account endpoints for the signed-in user.
 *
 * <p>GET /api/users/me: current profile
 * <p>PUT /api/users/me: change username and/or password (a password change needs currentPassword)
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserApiController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserResponseDto> me(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(userService.getUserByEmail(userDetails.getUsername()));
    }

    @PutMapping("/me")
    public ResponseEntity<UserResponseDto> updateMe(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UserUpdateDto dto
    ) {
        UserResponseDto me = userService.getUserByEmail(userDetails.getUsername());
        return ResponseEntity.ok(userService.updateProfile(me.getId(), dto));
    }
}
