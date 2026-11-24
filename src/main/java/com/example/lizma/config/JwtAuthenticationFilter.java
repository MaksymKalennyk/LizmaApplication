package com.example.lizma.config;

import com.example.lizma.model.Users;
import com.example.lizma.service.JwtService;
import com.example.lizma.service.UserService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String BEARER_PREFIX = "Bearer ";
    public static final String HEADER_NAME = "Authorization";
    /** Set when a bearer token was sent but is invalid/expired; the 401 response then asks to sign in again. */
    public static final String INVALID_TOKEN_ATTRIBUTE = JwtAuthenticationFilter.class.getName() + ".INVALID_TOKEN";

    private final JwtService jwtService;
    private final UserService userService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader(HEADER_NAME);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)
                || SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = authHeader.substring(BEARER_PREFIX.length()).trim();
        try {
            JwtService.TokenClaims claims = jwtService.parse(jwt);
            UserDetails userDetails = userService.userDetailsService().loadUserByUsername(claims.username());
            // The name alone isn't enough: a deleted account's name can be taken by a new one, which must
            // not inherit the old account's tokens.
            boolean sameAccount = userDetails instanceof Users user && user.getId().equals(claims.userId());
            if (!sameAccount || !userDetails.isEnabled()) { // suspended by a moderator: the token stops working
                request.setAttribute(INVALID_TOKEN_ATTRIBUTE, Boolean.TRUE);
                filterChain.doFilter(request, response);
                return;
            }

            var authToken = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authToken);
            SecurityContextHolder.setContext(context);
        } catch (JwtException | IllegalArgumentException | UsernameNotFoundException ex) {
            // Bad, expired or orphaned token: continue as anonymous. Public endpoints (sign-in, vanity pages)
            // keep working; protected ones answer 401 via the entry point in SecurityConfiguration.
            request.setAttribute(INVALID_TOKEN_ATTRIBUTE, Boolean.TRUE);
        }
        filterChain.doFilter(request, response);
    }
}
