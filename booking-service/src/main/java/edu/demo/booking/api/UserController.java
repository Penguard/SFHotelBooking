package edu.demo.booking.api;

import edu.demo.booking.api.AuthDtos.*;
import edu.demo.booking.service.AuthService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class UserController {
    private final AuthService auth;
    public UserController(AuthService auth) { this.auth = auth; }
    @PostMapping("/user/register") @ResponseStatus(HttpStatus.CREATED)
    public TokenView register(@Valid @RequestBody Credentials input) { return auth.register(input); }
    @PostMapping("/user/auth")
    public TokenView login(@Valid @RequestBody Credentials input) { return auth.login(input); }
    @PostMapping("/user") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasRole('ADMIN')")
    public UserView create(@Valid @RequestBody AdminUserInput input) { return auth.createUser(input); }
    @PatchMapping({"/user", "/user/{id}"}) @PreAuthorize("hasRole('ADMIN')")
    public UserView patch(@PathVariable(required = false) Long id, @RequestBody UpdateUserInput input) {
        Long target = id != null ? id : input.id();
        if (target == null) throw new ApiException(HttpStatus.BAD_REQUEST, "User id required");
        return auth.updateUser(target, input);
    }
    @DeleteMapping({"/user", "/user/{id}"}) @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable(required = false) Long id, @RequestParam(required = false) Long userId) {
        Long target = id != null ? id : userId;
        if (target == null) throw new ApiException(HttpStatus.BAD_REQUEST, "User id required");
        auth.deleteUser(target);
    }
    @GetMapping("/users") @PreAuthorize("hasRole('ADMIN')")
    public List<UserView> list() { return auth.users(); }
}
