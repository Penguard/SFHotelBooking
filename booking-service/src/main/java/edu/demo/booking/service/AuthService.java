package edu.demo.booking.service;

import edu.demo.booking.api.ApiException;
import edu.demo.booking.api.AuthDtos.*;
import edu.demo.booking.domain.User;
import edu.demo.booking.repo.BookingRepository;
import edu.demo.booking.repo.UserRepository;
import edu.demo.booking.security.TokenIssuer;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users;
    private final BookingRepository bookings;
    private final PasswordEncoder passwords;
    private final TokenIssuer issuer;
    public AuthService(UserRepository users, BookingRepository bookings, PasswordEncoder passwords, TokenIssuer issuer) {
        this.users = users; this.bookings = bookings; this.passwords = passwords; this.issuer = issuer;
    }
    @Transactional
    public TokenView register(Credentials credentials) {
        if (users.existsByUsername(credentials.username())) throw new ApiException(HttpStatus.CONFLICT, "Username already exists");
        User user = users.save(new User(credentials.username(), passwords.encode(credentials.password()), User.Role.USER));
        return issuer.userToken(user);
    }
    @Transactional(readOnly = true)
    public TokenView login(Credentials credentials) {
        User user = users.findByUsername(credentials.username())
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        if (!passwords.matches(credentials.password(), user.getPassword()))
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        return issuer.userToken(user);
    }
    @Transactional
    public UserView createUser(AdminUserInput input) {
        if (users.existsByUsername(input.username())) throw new ApiException(HttpStatus.CONFLICT, "Username already exists");
        if (input.role() == null) throw new ApiException(HttpStatus.BAD_REQUEST, "Role required");
        return UserView.from(users.save(new User(input.username(), passwords.encode(input.password()), input.role())));
    }
    @Transactional
    public UserView updateUser(long id, UpdateUserInput input) {
        User user = getUser(id);
        if (input.username() != null) {
            if (input.username().isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "Username cannot be blank");
            user.setUsername(input.username());
        }
        if (input.password() != null) {
            if (input.password().isBlank()) throw new ApiException(HttpStatus.BAD_REQUEST, "Password cannot be blank");
            user.setPassword(passwords.encode(input.password()));
        }
        if (input.role() != null) user.setRole(input.role());
        return UserView.from(user);
    }
    @Transactional
    public void deleteUser(long id) {
        if (bookings.existsByUserId(id)) throw new ApiException(HttpStatus.CONFLICT, "User has booking history");
        users.delete(getUser(id));
    }
    @Transactional(readOnly = true)
    public List<UserView> users() { return users.findAll().stream().map(UserView::from).toList(); }
    @Transactional(readOnly = true)
    public User getUser(long id) {
        return users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
    }
}

@Configuration
class PasswordConfiguration {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
}
