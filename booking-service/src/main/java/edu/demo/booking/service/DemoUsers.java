package edu.demo.booking.service;

import edu.demo.booking.domain.User;
import edu.demo.booking.repo.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DemoUsers implements CommandLineRunner {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    @Value("${app.seed.enabled:true}") private boolean enabled;
    @Value("${app.admin.password}") private String adminPassword;
    public DemoUsers(UserRepository users, PasswordEncoder encoder) { this.users = users; this.encoder = encoder; }
    @Override public void run(String... args) {
        if (!enabled || users.count() > 0) return;
        users.save(new User("admin", encoder.encode(adminPassword), User.Role.ADMIN));
        users.save(new User("demo", encoder.encode("User123!"), User.Role.USER));
    }
}
