package com.ssafy.brAIn.auth.oauth.kakao.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.brAIn.auth.jwt.JwtUtil;
import com.ssafy.brAIn.member.entity.Member;
import com.ssafy.brAIn.member.entity.Role;
import com.ssafy.brAIn.member.entity.Social;
import com.ssafy.brAIn.member.repository.MemberRepository;
import com.ssafy.brAIn.s3.S3Service;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;

@Service
@RequiredArgsConstructor
public class KakaoService {

    private final MemberRepository memberRepository;
    private final S3Service s3Service;

    @Value("${kakao.key.client-id}")
    private String clientId;

    @Value("${kakao.redirect-uri}")
    private String redirectUri;

    /** Web 버전 카카오 로그인 **/
    public String kakaoLogin(String code, HttpServletResponse response) {

        // 1. "인가 코드"로 "액세스 토큰" 요청
        String kakaoAccessToken = getAccessToken(code, redirectUri);

        // 2. 토큰으로 카카오 API 호출
        HashMap<String, Object> userInfo = getKakaoUserInfo(kakaoAccessToken);

        //3. 카카오ID로 회원가입 & 로그인 처리
        return kakaoUserLogin(userInfo, response);
    }

    //1. "인가 코드"로 "액세스 토큰" 요청
    private String getAccessToken(String code, String redirectUri) {

        // HTTP Header 생성
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");

        // HTTP Body 생성
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("client_id", clientId);
        body.add("redirect_uri", redirectUri);
        body.add("code", code);

        // HTTP 요청 보내기
        HttpEntity<MultiValueMap<String, String>> kakaoTokenRequest = new HttpEntity<>(body, headers);
        RestTemplate rt = new RestTemplate();
        ResponseEntity<String> response = rt.exchange(
                "https://kauth.kakao.com/oauth/token",
                HttpMethod.POST,
                kakaoTokenRequest,
                String.class
        );

        // HTTP 응답 (JSON) -> 액세스 토큰 파싱
        String responseBody = response.getBody();
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = null;
        try {
            jsonNode = objectMapper.readTree(responseBody);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        assert jsonNode != null;
        return jsonNode.get("access_token").asText(); //토큰 전송
    }

    //2. 토큰으로 카카오 API 호출
    private HashMap<String, Object> getKakaoUserInfo(String accessToken) {
        HashMap<String, Object> userInfo = new HashMap<>();

        // HTTP Header 생성
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + accessToken);
        headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");

        // HTTP 요청 보내기
        HttpEntity<MultiValueMap<String, String>> kakaoUserInfoRequest = new HttpEntity<>(headers);
        RestTemplate rt = new RestTemplate();
        ResponseEntity<String> response = rt.exchange(
                "https://kapi.kakao.com/v2/user/me",
                HttpMethod.POST,
                kakaoUserInfoRequest,
                String.class
        );

        // responseBody에 있는 정보를 꺼냄
        String responseBody = response.getBody();
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = null;
        try {
            jsonNode = objectMapper.readTree(responseBody);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        String email = jsonNode.get("kakao_account").get("email").asText();
        String nickname = jsonNode.get("properties").get("nickname").asText();

        userInfo.put("email", email);
        userInfo.put("nickname", nickname);

        return userInfo;
    }

    //3. 카카오ID로 회원가입 & 로그인 처리
    private String kakaoUserLogin(HashMap<String, Object> userInfo, HttpServletResponse response){
        String kakaoEmail = userInfo.get("email").toString();
        String nickName = userInfo.get("nickname").toString();

        // 이메일 중복 확인
        Member kakaoUser = memberRepository.findByEmail(kakaoEmail)
                .orElse(null);

        if (kakaoUser == null) {
            // 신규 사용자 회원가입
            kakaoUser = Member.builder()
                    .email(kakaoEmail)
                    .name(nickName)
                    .role(Role.USER)
                    .social(Social.Kakao)
                    .locked(true)
                    .loginFailCount(0)
                    .build();

            // 프로필 이미지 설정
            String profileImageUrl = s3Service.getRandomImageUrl();
            kakaoUser.updatePhoto(profileImageUrl);

            // 사용자 저장
            memberRepository.save(kakaoUser);
        }

        // 사용자 인증 객체 생성
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                kakaoUser,
                null,
                kakaoUser.getAuthorities()
        );

        // JWT 토큰 생성
        String accessToken = JwtUtil.createAccessToken(authentication);
        String refreshToken = JwtUtil.createRefreshToken(authentication);

        // Refresh Token을 DB에 저장
        kakaoUser.updateRefreshToken(refreshToken);
        memberRepository.save(kakaoUser);

        // Refresh Token을 쿠키에 저장
        Cookie cookie = new Cookie("refreshToken", refreshToken);
        cookie.setMaxAge(1209600); // 14일 설정
        cookie.setHttpOnly(true); // HttpOnly 설정
        cookie.setPath("/"); // 쿠키 경로 설정
        response.addCookie(cookie);

        // 액세스 토큰 반환
        return accessToken;
    }
}
