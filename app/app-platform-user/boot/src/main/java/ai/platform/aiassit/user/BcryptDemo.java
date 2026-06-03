package ai.platform.aiassit.user;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 *
 * @author zhouzhitong
 * @since 2026/6/3
 */
public class BcryptDemo {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String hash = encoder.encode("Admin@123456");
        System.out.println(hash);
    }
}