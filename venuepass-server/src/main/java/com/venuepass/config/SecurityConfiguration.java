package com.venuepass.config;

import com.venuepass.mapper.AccountMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    UserDetailsService userDetailsService(AccountMapper accounts, PasswordEncoder encoder,
            @Value("${venuepass.operator-password}") String operatorPassword,
            @Value("${venuepass.checker-password}") String checkerPassword) {
        return username -> {
            if ("operator".equals(username)) return User.withUsername(username).password(encoder.encode(operatorPassword)).roles("OPERATOR").build();
            if ("checker".equals(username)) return User.withUsername(username).password(encoder.encode(checkerPassword)).roles("CHECKER").build();
            String hash = accounts.passwordHash(username);
            if (hash == null) throw new UsernameNotFoundException(username);
            return User.withUsername(username).password(hash).roles("CUSTOMER").build();
        };
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/api/accounts").permitAll()
                .requestMatchers("/api/admin/**").hasRole("OPERATOR")
                .requestMatchers("/api/check-in/**").hasAnyRole("CHECKER", "OPERATOR")
                .requestMatchers("/api/reservations/**").hasRole("CUSTOMER")
                .requestMatchers("/actuator/**").hasRole("OPERATOR")
                .anyRequest().authenticated())
            .httpBasic(org.springframework.security.config.Customizer.withDefaults());
        return http.build();
    }
}
