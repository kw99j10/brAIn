package com.ssafy.brAIn.stomp.config;

import com.ssafy.brAIn.auth.jwt.JWTUtilForRoom;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CsrfChannelInterceptor implements ChannelInterceptor {

    private final JWTUtilForRoom jwtUtilForRoom;

    public CsrfChannelInterceptor(JWTUtilForRoom jwtUtilForRoom) {
        this.jwtUtilForRoom = jwtUtilForRoom;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {


        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        String conferenceToken = accessor.getFirstNativeHeader("AuthorizationRoom");


        if (conferenceToken != null && conferenceToken.startsWith("Bearer ")) {
            conferenceToken = conferenceToken.substring(7);
            if (!jwtUtilForRoom.isExpired(conferenceToken)) {
                UsernamePasswordAuthenticationToken authToken = (UsernamePasswordAuthenticationToken) jwtUtilForRoom.getAuthentication(conferenceToken);


                SecurityContextHolder.getContext().setAuthentication(authToken);

                accessor.setUser(authToken);

            } else {
                // 인증 토큰이 없거나 만료된 경우 예외 처리
                throw new AuthenticationCredentialsNotFoundException("Authentication credentials not found");
            }
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        return message;
    }

    @Override
    public void postSend(Message<?> message, MessageChannel channel, boolean sent) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    }

}
