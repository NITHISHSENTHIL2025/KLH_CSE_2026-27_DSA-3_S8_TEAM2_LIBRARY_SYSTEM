package com.intellilib.server.security;

import jakarta.servlet.http.HttpServletRequest;

public final class AuthHeader {
    private AuthHeader() {}
    public static String token(HttpServletRequest request) { return request.getHeader("X-Session-Token"); }
}
