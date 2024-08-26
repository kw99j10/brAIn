package com.ssafy.brAIn.openvidu.service;

import org.springframework.beans.factory.annotation.Value;

public class OpenViduService {

    @Value("${openvidu.url}")
    private String OPENVIDU_URL;

    @Value("${openvidu.secret}")
    private String SECRET;
}
