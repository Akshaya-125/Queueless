package com.queueless.backend.config;

import com.queueless.backend.entity.Role;
import com.queueless.backend.entity.ServiceCounter;
import com.queueless.backend.entity.User;
import com.queueless.backend.repository.ServiceCounterRepository;
import com.queueless.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import java.security.SecureRandom;

/**
 * Creates the first administrator and a few sample services.
 * No password is stored in the source code: it comes from ADMIN_PASSWORD, or is generated
 * once and printed in the startup log when ADMIN_PASSWORD is not set (local development only).
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository users;
    private final ServiceCounterRepository counters;
    private final PasswordEncoder encoder;
    private final String adminEmail;
    private final String adminPassword;
    private final boolean seedServices;

    public DataSeeder(UserRepository users, ServiceCounterRepository counters, PasswordEncoder encoder,
                      @Value("${queueless.admin.email}") String adminEmail,
                      @Value("${queueless.admin.password:}") String adminPassword,
                      @Value("${queueless.seed.sample-services:true}") boolean seedServices) {
        this.users = users;
        this.counters = counters;
        this.encoder = encoder;
        this.adminEmail = adminEmail.trim().toLowerCase();
        this.adminPassword = adminPassword;
        this.seedServices = seedServices;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!users.existsByEmail(adminEmail)) {
            String password = adminPassword;
            boolean generated = password == null || password.isBlank();
            if (generated) {
                password = randomPassword();
            }
            users.save(new User("Administrator", adminEmail, encoder.encode(password), Role.ADMIN));
            if (generated) {
                log.warn("ADMIN_PASSWORD is not set. Generated a one-time admin password for {}: {}", adminEmail, password);
            } else {
                log.info("Created administrator account {}", adminEmail);
            }
        }
        if (seedServices && counters.count() == 0) {
            counters.save(new ServiceCounter("Library Desk", "Central Library, Ground Floor", "Book issue and return", true));
            counters.save(new ServiceCounter("Computer Lab", "Block B, Room 204", "Lab slot and system allocation", true));
            counters.save(new ServiceCounter("Accounts Office", "Admin Block", "Fee payment and receipts", true));
            counters.save(new ServiceCounter("Health Centre", "Campus Clinic", "Doctor consultation", true));
            log.info("Created sample services");
        }
    }

    private static String randomPassword() {
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
