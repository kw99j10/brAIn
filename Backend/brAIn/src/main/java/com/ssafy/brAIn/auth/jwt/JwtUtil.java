package com.ssafy.brAIn.auth.jwt;

import com.ssafy.brAIn.member.entity.Member;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtUtil {

    private static SecretKey key;

    @Value("${jwt.secret}")
    public void setKey(String secret) {
        key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    // 토큰 만료 시간 설정
    private static final long accessTokenExpiration = 1209600000; // 10분 (1000 = 1초)
    private static final long refreshTokenExpiration = 1209600000; // 14일

    public static String createAccessToken(Authentication auth) {
        // 토큰에 포함될 사용자 정보
        String email;
        String role;
        String name;
        String social;

        // 일반 로그인 유저인지 확인
        if (auth.getPrincipal() instanceof Member) {
            Member member = (Member) auth.getPrincipal();
            email = member.getEmail();
            name = member.getName();
            role = member.getRole().name();
            social = member.getSocial().name();
        // 일반 로그인 유저인지 확인
        } else {
            // Oauth(구글) 로그인 유저인지 확인
            DefaultOAuth2User oauth2User = (DefaultOAuth2User) auth.getPrincipal();
            email = oauth2User.getAttribute("email");
            name = oauth2User.getAttribute("name");
            role = "USER"; // 기본값 설정
            social = "Google";
        }

        // access토큰 생성
        return Jwts.builder()
                .claim("email", email)
                .claim("name", name)
                .claim("role", role)
                .claim("social", social)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
                .signWith(key)
                .compact();
    }

    // refreshToken 생성 메서드
    public static String createRefreshToken(Authentication auth) {
        // 토큰에 포함할 사용자 정보
        String email;
        String role;
        String social;

        // 일반 로그인 유저인지 확인
        if (auth.getPrincipal() instanceof Member) {
            Member member = (Member) auth.getPrincipal();
            email = member.getEmail();
            role = member.getRole().name();
            social = member.getSocial().name();
        } else {
            // Oauth(구글) 로그인 유저인지 확인
            DefaultOAuth2User oauth2User = (DefaultOAuth2User) auth.getPrincipal();
            email = oauth2User.getAttribute("email");
            role = "USER"; // 기본값 설정
            social = "Google";
        }

        // refresh토큰 생성
        return Jwts.builder()
                .claim("email", email)
                .claim("role", role)
                .claim("social", social)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpiration))
                .signWith(key)
                .compact();
    }

    // JWT 까주는 함수
    public static Claims extractToken(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims;
    }

    // Token에서 이메일 추출
    public static String getEmail(String token) {
        Claims claims = extractToken(token);
        return claims.get("email").toString();
    }

    // Token에서 회의룸 추출
    public static String getConferenceRoomId(String token){
        Claims claims = extractToken(token);
        return claims.get("roomId").toString();
    }
}
