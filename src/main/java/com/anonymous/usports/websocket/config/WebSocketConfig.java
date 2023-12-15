package com.anonymous.usports.websocket.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker // STOMP를 사용 안 하고 EnableWebSocket으로 사용할 수 있다
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry
                .addEndpoint("/ws/chat")  // URL 또는 URI
                .setAllowedOriginPatterns("*")
                .withSockJS(); // 소켓을 지원하지 않는 브라우저라면, sockJS를 사용
    }

    // message 브로커를 활성화 시키는 설정
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // 메세지 구독 url (topic을 구독)
        registry.enableSimpleBroker("/sub");

        // 메세지 발행 url
        registry.setApplicationDestinationPrefixes("/pub");

    }
}
