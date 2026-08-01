package com.project.Splitwise.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * The authenticated caller, read from the security context rather than from anything the
 * client sent in a body or path.
 *
 * <p>That distinction is the point: a request may name any user id it likes in its payload,
 * but who the caller <em>is</em> comes only from a verified token.
 */
@Component
public class CurrentUser {

    public Long id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Long userId)) {
            throw new IllegalStateException(
                    "No authenticated user; this method must not be reachable anonymously");
        }
        return userId;
    }
}
