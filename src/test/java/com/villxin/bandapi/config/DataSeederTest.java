package com.villxin.bandapi.config;

import com.villxin.bandapi.entity.User;
import com.villxin.bandapi.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Admin seeding paths: fresh create, already-correct skip, and email rename. */
class DataSeederTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private DataSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new DataSeeder(userRepository, passwordEncoder);
        ReflectionTestUtils.setField(seeder, "adminEmail", "management@villxin.com");
        ReflectionTestUtils.setField(seeder, "adminPassword", "secret");
        ReflectionTestUtils.setField(seeder, "officialEmail", "");
        ReflectionTestUtils.setField(seeder, "officialUsername", "villxin");
        when(passwordEncoder.encode("secret")).thenReturn("hashed");
    }

    @Test
    void createsAdminWhenNoneExists() {
        when(userRepository.existsByEmail("management@villxin.com")).thenReturn(false);
        when(userRepository.findFirstByRole("ADMIN")).thenReturn(Optional.empty());

        seeder.run(new DefaultApplicationArguments());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals("management@villxin.com", saved.getValue().getEmail());
        assertEquals("ADMIN", saved.getValue().getRole());
        assertEquals("hashed", saved.getValue().getPassword());
    }

    @Test
    void skipsWhenAdminAlreadyHasConfiguredEmail() {
        when(userRepository.existsByEmail("management@villxin.com")).thenReturn(true);

        seeder.run(new DefaultApplicationArguments());

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void renamesExistingAdminWhenEmailChanges() {
        when(userRepository.existsByEmail("management@villxin.com")).thenReturn(false);
        User old = new User();
        old.setEmail("admin@villxin.com");
        old.setRole("ADMIN");
        old.setPassword("existing-hash");
        when(userRepository.findFirstByRole("ADMIN")).thenReturn(Optional.of(old));

        seeder.run(new DefaultApplicationArguments());

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertEquals("management@villxin.com", saved.getValue().getEmail());
        // rename keeps the existing password hash — no re-encode
        assertEquals("existing-hash", saved.getValue().getPassword());
    }
}
