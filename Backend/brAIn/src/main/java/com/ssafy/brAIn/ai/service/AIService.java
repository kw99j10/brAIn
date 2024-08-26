package com.ssafy.brAIn.ai.service;

import com.ssafy.brAIn.ai.response.AIAssistant;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AIService {

    private final WebClient webClient;

    public AIService(WebClient webClient) {
        this.webClient = webClient;
    }

    public AIAssistant makeAIAssistant(String subject) {
        String url="/thread/start";

        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("subject", subject);

        return webClient
                .post()
                .uri(url)
                .bodyValue(requestBody)
                .retrieve().bodyToMono(AIAssistant.class)
                .block();
    }

    public String addPostIt(String content, String threadId) {
        String url="/postIt/add";
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("postIt", content);
        requestBody.put("threadId", threadId);

        return webClient
                .post()
                .uri(url)
                .bodyValue(requestBody)
                .retrieve().bodyToMono(String.class)
                .block();
    }



    public String makePostIt(String threadId, String assistantId) {
        String url="/postIt/make";
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("assistantId", assistantId);
        requestBody.put("threadId", threadId);

        return webClient
                .post()
                .uri(url)
                .bodyValue(requestBody)
                .retrieve().bodyToMono(String.class)
                .block();
    }


    public String addComment(String threadId, String postIt, String comment) {
        String url="/comment/add";
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("threadId", threadId);
        requestBody.put("postIt", postIt);
        requestBody.put("comment", comment);

        return webClient
                .post()
                .uri(url)
                .bodyValue(requestBody)
                .retrieve().bodyToMono(String.class)
                .block();
    }


    public String makeSummary(String threadId, String assistantId) {
        String url = "/summary/make";
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("assistantId", assistantId);
        requestBody.put("threadId", threadId);

        return webClient
                .post()
                .uri(url)
                .bodyValue(requestBody)
                .retrieve().bodyToMono(String.class)
                .block();
    }


    public Mono<String> personaMake(String idea, String threadId, String assistantId) {
        String url = "/persona/make";
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("idea", idea);
        requestBody.put("assistantId", assistantId);
        requestBody.put("threadId", threadId);

        return webClient
                .post()
                .uri(url)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class);
    }

    public Mono<String> swotMake(String idea, List<String> details, String threadId, String assistantId) {
        String url="/swot/make";
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("idea", idea);
        requestBody.put("assistantId", assistantId);
        requestBody.put("threadId", threadId);
        requestBody.put("details", details.toString());

        return webClient
                .post()
                .uri(url)
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class);
    }
}
