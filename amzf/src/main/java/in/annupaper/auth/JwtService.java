package in.annupaper.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JWT Service for token generation and validation.
 * 
 * Simple HS256 implementation. In production, consider using:
 * - Auth0 Java JWT library
 * - JJWT (io.jsonwebtoken)
 * - Nimbus JOSE+JWT
 */
public final class JwtService {
    private static final Logger log = LoggerFactory.getLogger(JwtService.class);
    
    private final String secret;
    private final long expirationMs;
    
    // Token blacklist for logout
    private final Map<String, Instant> blacklist = new ConcurrentHashMap<>();
    
    public JwtService(String secret, long expirationMs) {
        this.secret = secret;
        this.expirationMs = expirationMs;
    }
    
    /**
     * Generate JWT token for user.
     */
    public String generateToken(String userId, String email, String role) {
        long now = System.currentTimeMillis();
        long exp = now + expirationMs;
        
        // Header (always the same for HS256)
        String header = base64Encode("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        
        // Payload
        String payload = base64Encode(String.format(
            "{\"sub\":\"%s\",\"email\":\"%s\",\"role\":\"%s\",\"iat\":%d,\"exp\":%d}",
            userId, email, role, now / 1000, exp / 1000
        ));
        
        // Signature
        String signature = sign(header + "." + payload);
        
        return header + "." + payload + "." + signature;
    }
    
    /**
     * Validate token and extract user ID.
     * Returns null if invalid.
     */
    public String validateAndGetUserId(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        
        // Remove "Bearer " prefix if present
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                log.debug("Invalid token format");
                return null;
            }
            
            String header = parts[0];
            String payload = parts[1];
            String signature = parts[2];
            
            // Verify signature
            String expectedSig = sign(header + "." + payload);
            if (!expectedSig.equals(signature)) {
                log.debug("Invalid token signature");
                return null;
            }
            
            // Decode payload
            String payloadJson = base64Decode(payload);
            
            // Parse claims (simple JSON parsing)
            String sub = extractClaim(payloadJson, "sub");
            String expStr = extractClaim(payloadJson, "exp");
            
            if (sub == null || expStr == null) {
                log.debug("Missing required claims");
                return null;
            }
            
            // Check expiration
            long exp = Long.parseLong(expStr) * 1000;
            if (System.currentTimeMillis() > exp) {
                log.debug("Token expired");
                return null;
            }
            
            // Check blacklist
            if (blacklist.containsKey(token)) {
                log.debug("Token is blacklisted");
                return null;
            }
            
            return sub;
            
        } catch (Exception e) {
            log.debug("Token validation error: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Extract all claims from token.
     */
    public TokenClaims getClaims(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }

        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return null;
            }

            String payloadJson = base64Decode(parts[1]);

            return new TokenClaims(
                extractClaim(payloadJson, "sub"),
                extractClaim(payloadJson, "email"),
                extractClaim(payloadJson, "role"),
                Long.parseLong(extractClaim(payloadJson, "iat")) * 1000,
                Long.parseLong(extractClaim(payloadJson, "exp")) * 1000
            );

        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Blacklist a token (for logout).
     */
    public void blacklistToken(String token) {
        if (token != null) {
            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
            blacklist.put(token, Instant.now());
        }
    }
    
    /**
     * Clean up expired tokens from blacklist.
     */
    public void cleanupBlacklist() {
        long now = System.currentTimeMillis();
        blacklist.entrySet().removeIf(e -> {
            // Keep in blacklist for 24 hours after token expiration
            return e.getValue().toEpochMilli() + expirationMs + 86400000 < now;
        });
    }
    
    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to sign JWT", e);
        }
    }
    
    private String base64Encode(String data) {
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(data.getBytes(StandardCharsets.UTF_8));
    }
    
    private String base64Decode(String data) {
        return new String(Base64.getUrlDecoder().decode(data), StandardCharsets.UTF_8);
    }
    
    private String extractClaim(String json, String key) {
        // Simple JSON extraction (no external deps)
        String search = "\"" + key + "\":";
        int idx = json.indexOf(search);
        if (idx < 0) return null;
        
        int start = idx + search.length();
        
        // Skip whitespace
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) {
            start++;
        }
        
        if (start >= json.length()) return null;
        
        char c = json.charAt(start);
        
        if (c == '"') {
            // String value
            int end = json.indexOf('"', start + 1);
            if (end < 0) return null;
            return json.substring(start + 1, end);
        } else {
            // Number or other
            int end = start;
            while (end < json.length() && !Character.isWhitespace(json.charAt(end)) 
                   && json.charAt(end) != ',' && json.charAt(end) != '}') {
                end++;
            }
            return json.substring(start, end);
        }
    }
    
    /**
     * Token claims record.
     */
    public record TokenClaims(
        String userId,
        String email,
        String role,
        long issuedAt,
        long expiresAt
    ) {
        public boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
        
        public boolean isAdmin() {
            return "ADMIN".equals(role);
        }
    }
}
