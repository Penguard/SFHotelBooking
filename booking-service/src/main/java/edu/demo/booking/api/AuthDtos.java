package edu.demo.booking.api;

import edu.demo.booking.domain.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {}
    public record Credentials(@NotBlank @Size(min = 3, max = 60) String username,
                              @NotBlank @Size(min = 8) String password) {}
    public record TokenView(String token, String tokenType, long expiresInSeconds, Long userId, String role) {}
    public record AdminUserInput(@NotBlank String username, @NotBlank @Size(min = 8) String password, User.Role role) {}
    public record UpdateUserInput(Long id, String username, String password, User.Role role) {}
    public record UserView(Long id, String username, User.Role role) {
        public static UserView from(User user) { return new UserView(user.getId(), user.getUsername(), user.getRole()); }
    }
}
