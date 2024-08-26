package com.ssafy.brAIn.auth.jwt;

import com.ssafy.brAIn.history.entity.MemberHistory;
import com.ssafy.brAIn.history.entity.MemberHistoryId;
import com.ssafy.brAIn.history.model.Role;
import com.ssafy.brAIn.member.service.MemberService;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JWTUtilForRoom {

    private final SecretKey secretKey;
    private final MemberService memberService;

    public JWTUtilForRoom(@Value("${jwt.room.secret}")String secret, MemberService memberService) {

        this.secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), Jwts.SIG.HS256.key().build().getAlgorithm());
        this.memberService = memberService;
    }

    public String getUsername(String token) {

        return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload().get("username", String.class);
    }



    public String getRole(String token) {

        return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload().get("role", String.class);
    }

    public Boolean isExpired(String token) {

        return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload().getExpiration().before(new Date());
    }

    public String getCategory(String token) {
        return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload().get("category", String.class);
    }

    public String getRoomId(String token){
        return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload().get("roomId", String.class);
    }


    public String getNickname(String token){
        return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload().get("nickname", String.class);

    }

    public Authentication getAuthentication(String token){
        String username = getUsername(token);
        int memberId=memberService.findByEmail(username).get().getId();
        String role = getRole(token);
        String nickname = getNickname(token);
        int roomId = Integer.parseInt(getRoomId(token));
        MemberHistoryId memberHistoryId=new MemberHistoryId(memberId,roomId);
        MemberHistory memberHistory=MemberHistory.builder().id(memberHistoryId)
                .role(Role.valueOf(role))
                .nickName(nickname)
                .build();

        return new UsernamePasswordAuthenticationToken(memberHistory, null, memberHistory.getAuthorities());
    }


    public String createJwt(String category,String username, String role,String nickname,String roomId, Long expiredMs) {

        return Jwts.builder()
                .claim("category",category)
                .claim("username", username)
                .claim("role", role)
                .claim("nickname", nickname)
                .claim("roomId", roomId)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + expiredMs))
                .signWith(secretKey)
                .compact();
    }


}
