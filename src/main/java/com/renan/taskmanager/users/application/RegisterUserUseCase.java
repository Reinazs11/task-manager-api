package com.renan.taskmanager.users.application;

import com.renan.taskmanager.users.api.UserResponse;
import com.renan.taskmanager.users.domain.Email;
import com.renan.taskmanager.users.domain.Password;
import com.renan.taskmanager.users.domain.User;
import com.renan.taskmanager.users.domain.UserAlreadyExistsException;
import com.renan.taskmanager.users.domain.PasswordHasher;
import com.renan.taskmanager.users.domain.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application use case: register a new user.
 *
 * <p>Orchestrates the registration flow:</p>
 * <ol>
 *   <li>Convert raw inputs into domain value objects (validation happens here).</li>
 *   <li>Reject if the email is already in use.</li>
 *   <li>Hash the password (infrastructure concern, via {@link PasswordHasher}).</li>
 *   <li>Persist the user.</li>
 *   <li>Return the public representation (no secrets leaked).</li>
 * </ol>
 *
 * <p><b>Why @Transactional?</b>
 * The "check then insert" sequence must be atomic to prevent races where two
 * concurrent registrations slip past the uniqueness check before either inserts.
 * The database unique constraint is the last line of defense; this transaction
 * is the first.</p>
 *
 * <p><b>Why does the use case return a UserResponse instead of a domain User?</b>
 * The application layer talks to controllers; DTOs are the natural currency.
 * Returning the domain User would couple the API surface to internal types.</p>
 */
@Service
public class RegisterUserUseCase {

    private final UserRepository userRepository;
    private final PasswordHasher passwordHasher;

    public RegisterUserUseCase(UserRepository userRepository, PasswordHasher passwordHasher) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
    }

    @Transactional
    public UserResponse execute(String email, String plainPassword, String name) {
        Email emailVo = new Email(email);

        if (userRepository.existsByEmail(emailVo)) {
            throw new UserAlreadyExistsException(emailVo);
        }

        // Domain validation of strength rules happens inside Password ctor.
        Password plainPasswordVo = new Password(plainPassword);
        String hash = passwordHasher.hash(plainPasswordVo);

        // Build the user with the HASHED password. We use Password.fromHash so
        // the hash bypasses strength validation (it would fail otherwise).
        User user = User.create(emailVo, Password.fromHash(hash), name);
        User saved = userRepository.save(user);

        return new UserResponse(
                saved.getId().value(),
                saved.getEmail().value(),
                saved.getName()
        );
    }
}
