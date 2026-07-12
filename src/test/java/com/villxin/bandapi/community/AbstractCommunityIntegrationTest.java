package com.villxin.bandapi.community;

import com.villxin.bandapi.entity.User;
import com.villxin.bandapi.repository.UserRepository;
import com.villxin.bandapi.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Shared base for community integration tests: one Postgres container and one
 * cached Spring context across all subclasses (real migrations,
 * ddl-auto=validate — same approach as {@code BandApiApplicationTests}).
 *
 * <p>Uses the Testcontainers <em>singleton container</em> pattern (manual
 * start, no {@code @Testcontainers}/{@code @Container}): the JUnit extension
 * would stop the container after each test class, breaking the Spring context
 * cached across subclasses. Ryuk reaps the container when the JVM exits.
 * DB state is shared between classes, so each test uses unique usernames.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(CapturingEmailSenderConfig.class)
public abstract class AbstractCommunityIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected JwtUtil jwtUtil;

    /** Creates a verified community member directly (bypassing the email flow). */
    protected User createMember(String username, boolean official) {
        User user = new User();
        user.setEmail(username + "@test.example");
        user.setUsername(username);
        user.setDisplayName(username);
        user.setEmailVerified(true);
        user.setOfficial(official);
        return userRepository.save(user);
    }

    protected String bearer(User user) {
        return "Bearer " + jwtUtil.generate(user.getEmail(), user.getRole());
    }
}
