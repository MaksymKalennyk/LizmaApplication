package com.example.lizma.model;

import com.example.lizma.model.enums.Role;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Entity
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "users")
public class Users implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Role role;

    /** Suspended by a moderator: can't sign in, existing tokens stop working, content is removed, nobody can find them. */
    @Builder.Default
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean banned = false;

    /** When the user accepted the Terms of Use and Privacy Policy (at sign-up). */
    private Instant termsAcceptedAt;

    /** Shown instead of the username where set; the username stays the unique handle. */
    @Column(length = 50)
    private String displayName;

    /** The current profile photo (see UserAvatar), null without one. */
    @Column(length = 40, unique = true)
    private String avatarToken;

    // What the user wants push notifications about (Profile -> Notifications); all on by default.
    @Builder.Default
    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean pushFriendRequests = true;

    @Builder.Default
    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean pushReactions = true;

    @Builder.Default
    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean pushComments = true;

    /** "What are you listening to today?" at 13:00 Kyiv time, unless they've posted that day. */
    @Builder.Default
    @Column(nullable = false, columnDefinition = "boolean default true")
    private boolean pushDailyReminder = true;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.name()));
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return !banned;
    }
}
