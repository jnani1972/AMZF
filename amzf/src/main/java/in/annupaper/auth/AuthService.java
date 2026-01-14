package in.annupaper.auth;

import in.annupaper.domain.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.*;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Authentication service for user login/registration.
 */
public final class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    
    private final DataSource dataSource;
    private final JwtService jwtService;
    
    public AuthService(DataSource dataSource, JwtService jwtService) {
        this.dataSource = dataSource;
        this.jwtService = jwtService;
    }
    
    /**
     * Login with email and password.
     */
    public LoginResult login(String email, String password) {
        if (email == null || password == null) {
            return LoginResult.failure("Email and password required");
        }

        try (Connection conn = dataSource.getConnection()) {
            String sql = "SELECT * FROM users WHERE email = ? AND status = 'ACTIVE' AND deleted_at IS NULL";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, email.toLowerCase().trim());

                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return LoginResult.failure("Invalid email or password");
                    }

                    String storedHash = rs.getString("password_hash");
                    if (!verifyPassword(password, storedHash)) {
                        return LoginResult.failure("Invalid email or password");
                    }

                    String userId = rs.getString("user_id");
                    String displayName = rs.getString("display_name");
                    String role = rs.getString("role");

                    String token = jwtService.generateToken(userId, email, role);

                    log.info("User logged in: {} ({})", email, userId);
                    return LoginResult.success(token, userId, displayName, role);
                }
            }
        } catch (Exception e) {
            log.error("Login error: {}", e.getMessage(), e);
            return LoginResult.failure("Login failed");
        }
    }
    
    /**
     * Register new user.
     */
    public RegisterResult register(String email, String password, String displayName) {
        if (email == null || password == null || displayName == null) {
            return RegisterResult.failure("All fields required");
        }
        
        email = email.toLowerCase().trim();
        
        if (!isValidEmail(email)) {
            return RegisterResult.failure("Invalid email format");
        }
        
        if (password.length() < 8) {
            return RegisterResult.failure("Password must be at least 8 characters");
        }
        
        try (Connection conn = dataSource.getConnection()) {
            // Check if email exists
            String checkSql = "SELECT user_id FROM users WHERE email = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                ps.setString(1, email);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return RegisterResult.failure("Email already registered");
                    }
                }
            }

            // Create user
            String userId = "U" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            String passwordHash = hashPassword(password);

            String insertSql = """
                INSERT INTO users (user_id, email, display_name, password_hash, role, status, version)
                VALUES (?, ?, ?, ?, 'USER', 'ACTIVE', 1)
                """;

            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setString(1, userId);
                ps.setString(2, email);
                ps.setString(3, displayName);
                ps.setString(4, passwordHash);
                ps.executeUpdate();
            }

            // Create default portfolio
            String portfolioId = "P" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            String portfolioSql = """
                INSERT INTO portfolios (portfolio_id, user_id, name, total_capital, version)
                VALUES (?, ?, 'Default Portfolio', 0, 1)
                """;

            try (PreparedStatement ps = conn.prepareStatement(portfolioSql)) {
                ps.setString(1, portfolioId);
                ps.setString(2, userId);
                ps.executeUpdate();
            }

            String token = jwtService.generateToken(userId, email, "USER");

            log.info("User registered: {} ({}) version 1", email, userId);
            return RegisterResult.success(token, userId);

        } catch (Exception e) {
            log.error("Registration error: {}", e.getMessage(), e);
            return RegisterResult.failure("Registration failed");
        }
    }

    /**
     * Create admin user (called at startup if admin doesn't exist).
     */
    public RegisterResult createAdminUser(String email, String password, String displayName) {
        if (email == null || password == null || displayName == null) {
            return RegisterResult.failure("All fields required");
        }

        email = email.toLowerCase().trim();

        try (Connection conn = dataSource.getConnection()) {
            // Check if email exists
            String checkSql = "SELECT user_id FROM users WHERE email = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(checkSql)) {
                ps.setString(1, email);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return RegisterResult.failure("Email already registered");
                    }
                }
            }

            // Create admin user
            String userId = "UADMIN" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
            String passwordHash = hashPassword(password);

            String insertSql = """
                INSERT INTO users (user_id, email, display_name, password_hash, role, status, version)
                VALUES (?, ?, ?, ?, 'ADMIN', 'ACTIVE', 1)
                """;

            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setString(1, userId);
                ps.setString(2, email);
                ps.setString(3, displayName);
                ps.setString(4, passwordHash);
                ps.executeUpdate();
            }

            String token = jwtService.generateToken(userId, email, "ADMIN");

            log.info("Admin user created: {} ({}) version 1", email, userId);
            return RegisterResult.success(token, userId);

        } catch (Exception e) {
            log.error("Admin creation error: {}", e.getMessage(), e);
            return RegisterResult.failure("Admin creation failed");
        }
    }

    /**
     * Check if admin user exists.
     */
    public boolean adminExists() {
        String sql = "SELECT COUNT(*) FROM users WHERE role = 'ADMIN' AND deleted_at IS NULL";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (Exception e) {
            log.error("Error checking admin existence: {}", e.getMessage(), e);
        }

        return false;
    }

    /**
     * Logout (blacklist token).
     */
    public void logout(String token) {
        jwtService.blacklistToken(token);
    }
    
    /**
     * Get user by ID.
     */
    public Optional<User> getUserById(String userId) {
        String sql = "SELECT * FROM users WHERE user_id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            
            ps.setString(1, userId);
            
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapUser(rs));
                }
            }
        } catch (Exception e) {
            log.error("Error getting user: {}", e.getMessage(), e);
        }
        
        return Optional.empty();
    }
    
    /**
     * Change password.
     */
    public boolean changePassword(String userId, String oldPassword, String newPassword) {
        if (newPassword == null || newPassword.length() < 8) {
            return false;
        }
        
        try (Connection conn = dataSource.getConnection()) {
            // Verify old password
            String sql = "SELECT password_hash FROM users WHERE user_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, userId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return false;
                    if (!verifyPassword(oldPassword, rs.getString("password_hash"))) {
                        return false;
                    }
                }
            }
            
            // Update password
            String updateSql = "UPDATE users SET password_hash = ?, updated_at = NOW() WHERE user_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setString(1, hashPassword(newPassword));
                ps.setString(2, userId);
                ps.executeUpdate();
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("Error changing password: {}", e.getMessage(), e);
            return false;
        }
    }
    
    private String hashPassword(String password) {
        try {
            // Generate salt
            byte[] salt = new byte[16];
            RANDOM.nextBytes(salt);
            
            // Hash with salt
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            
            // Encode as salt$hash
            return Base64.getEncoder().encodeToString(salt) + "$" + 
                   Base64.getEncoder().encodeToString(hash);
                   
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash password", e);
        }
    }
    
    private boolean verifyPassword(String password, String storedHash) {
        try {
            String[] parts = storedHash.split("\\$");
            if (parts.length != 2) return false;
            
            byte[] salt = Base64.getDecoder().decode(parts[0]);
            byte[] expectedHash = Base64.getDecoder().decode(parts[1]);
            
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt);
            byte[] actualHash = md.digest(password.getBytes(StandardCharsets.UTF_8));
            
            return MessageDigest.isEqual(expectedHash, actualHash);
            
        } catch (Exception e) {
            return false;
        }
    }
    
    private boolean isValidEmail(String email) {
        return email != null && email.contains("@") && email.contains(".");
    }
    
    private User mapUser(ResultSet rs) throws Exception {
        Timestamp deletedTs = rs.getTimestamp("deleted_at");
        Instant deletedAt = deletedTs != null ? deletedTs.toInstant() : null;
        int version = rs.getInt("version");

        return new User(
            rs.getString("user_id"),
            rs.getString("email"),
            rs.getString("display_name"),
            rs.getString("password_hash"),
            rs.getString("role"),
            rs.getString("status"),
            null, // preferences
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant(),
            deletedAt,
            version
        );
    }
    
    // Result records
    
    public record LoginResult(
        boolean success,
        String token,
        String userId,
        String displayName,
        String role,
        String error
    ) {
        public static LoginResult success(String token, String userId, String displayName, String role) {
            return new LoginResult(true, token, userId, displayName, role, null);
        }
        public static LoginResult failure(String error) {
            return new LoginResult(false, null, null, null, null, error);
        }
    }
    
    public record RegisterResult(
        boolean success,
        String token,
        String userId,
        String error
    ) {
        public static RegisterResult success(String token, String userId) {
            return new RegisterResult(true, token, userId, null);
        }
        public static RegisterResult failure(String error) {
            return new RegisterResult(false, null, null, error);
        }
    }
}
