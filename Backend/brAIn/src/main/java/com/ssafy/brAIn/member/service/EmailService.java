package com.ssafy.brAIn.member.service;

import com.ssafy.brAIn.member.entity.VerificationCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {
    @Value("${spring.mail.username}")
    private String email;
    private final Integer EXPIRATION_TIME_IN_MINUTES = 5; //제한시간 5분
    private final JavaMailSender mailSender;

    @Qualifier("redisTemplate1")
    private final RedisTemplate<String, Object> redisTemplate1;

    // 인증 코드 발송
    public void sendEmail(String to, LocalDateTime sendAt) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(email); //발신자 이메일
        message.setTo(to); //수신자 이메일
        message.setSubject(String.format("Email Verification For %s", to)); //이메일 제목

        VerificationCode code = generateVerificationCode(sendAt);
        redisTemplate1.opsForValue().set(to, code, EXPIRATION_TIME_IN_MINUTES, TimeUnit.MINUTES);
        String text = code.generateCodeMessage();
        message.setText(text);
        mailSender.send(message);
    }

    // 인증코드 검증
    public void verifyEmail(String email, String code, LocalDateTime verifiedAt) {
        VerificationCode verificationCode = (VerificationCode) redisTemplate1.opsForValue().get(email);
        if (verificationCode == null) {
            throw new NoSuchElementException("Verification code not found");
        }

        // 인증 코드가 만료된 상태
        if (verificationCode.isExpired(verifiedAt)) {
            throw new IllegalStateException("Verification code expired");
        }

        // 인증 코드가 일치하는지 확인
        if (!verificationCode.getCode().equals(code)) {
            throw new IllegalArgumentException("Invalid verification code");
        }

        redisTemplate1.delete(email); // 인증 완료 후 Redis에서 삭제
    }

    // 6자리 랜덤 인증 숫자
    private String generate6DigitCode() {
        SecureRandom random = new SecureRandom();
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }

    // 인증코드 생성
    private VerificationCode generateVerificationCode(LocalDateTime sentAt) {
        String code = generate6DigitCode();
        return VerificationCode.builder()
                .code(code)
                .createAt(sentAt)
                .expiryTime(EXPIRATION_TIME_IN_MINUTES)
                .build();
    }
}
