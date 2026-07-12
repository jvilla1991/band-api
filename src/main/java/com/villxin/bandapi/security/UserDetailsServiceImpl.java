package com.villxin.bandapi.security;

import com.villxin.bandapi.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmail(email)
                .map(user -> new org.springframework.security.core.userdetails.User(
                        user.getEmail(),
                        // magic-link members have no password; "" can never match a BCrypt hash,
                        // so password login always fails for them (they log in via magic link)
                        user.getPassword() != null ? user.getPassword() : "",
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()))
                ))
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + email));
    }
}
