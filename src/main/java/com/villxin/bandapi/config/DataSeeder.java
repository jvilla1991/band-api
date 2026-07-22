package com.villxin.bandapi.config;

import com.villxin.bandapi.entity.User;
import com.villxin.bandapi.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.email:}")
    private String adminEmail;

    @Value("${admin.password:}")
    private String adminPassword;

    @Value("${community.official.email:}")
    private String officialEmail;

    @Value("${community.official.username:villxin}")
    private String officialUsername;

    public DataSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedAdmin();
        seedOfficialAccount();
    }

    private void seedAdmin() {
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            log.info("ADMIN_EMAIL or ADMIN_PASSWORD not set — skipping admin seed");
            return;
        }

        String email = adminEmail.toLowerCase().trim();
        if (userRepository.existsByEmail(email)) {
            log.info("Admin user already exists — skipping seed");
            return;
        }

        // ADMIN_EMAIL is authoritative: if the admin was seeded under a previous
        // address, rename it (keeps the same password and any owned rows) rather
        // than piling up a second admin account.
        User existingAdmin = userRepository.findFirstByRole("ADMIN").orElse(null);
        if (existingAdmin != null) {
            log.info("Admin email changed — renaming {} to {}", existingAdmin.getEmail(), email);
            existingAdmin.setEmail(email);
            userRepository.save(existingAdmin);
            return;
        }

        User admin = new User();
        admin.setEmail(email);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setRole("ADMIN");
        userRepository.save(admin);

        log.info("Admin user seeded: {}", email);
    }

    /**
     * Seeds the official villxin community account. The official flag is only
     * ever set here (or manually in the DB) — never through the API. The
     * account logs in via magic link like everyone else (no password).
     */
    private void seedOfficialAccount() {
        if (officialEmail.isBlank()) {
            log.info("OFFICIAL_EMAIL not set — skipping official account seed");
            return;
        }

        String username = officialUsername.toLowerCase().trim();
        if (userRepository.existsByUsername(username)) {
            log.info("Official account already exists — skipping seed");
            return;
        }

        User official = userRepository.findByEmail(officialEmail.toLowerCase().trim())
                .orElseGet(User::new);
        official.setEmail(officialEmail.toLowerCase().trim());
        official.setUsername(username);
        official.setDisplayName("villxin");
        official.setOfficial(true);
        official.setEmailVerified(true);
        userRepository.save(official);

        log.info("Official community account seeded: {}", username);
    }
}
