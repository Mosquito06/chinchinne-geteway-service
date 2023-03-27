package com.chinchinne.gatewayservice.filter;

import com.chinchinne.gatewayservice.model.Authorization;
import com.chinchinne.gatewayservice.model.ErrorCode;
import com.chinchinne.gatewayservice.model.ErrorResponse;
import com.chinchinne.gatewayservice.request.GrantType;
import com.chinchinne.gatewayservice.response.IntroSpecResponse;
import com.chinchinne.gatewayservice.response.TokenResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class AuthFilter extends AbstractGatewayFilterFactory<AuthFilter.Config>
{
    private WebClient.Builder webClientBuilder;

    private ObjectMapper objectMapper;

    @Value("${auth.clientId}")
    private String CLIENT_ID;

    @Value("${auth.secret}")
    private String CLIENT_SECRET;

    @Value("${auth.header}")
    private String CHINCHINNE_AUTHORIZATION;

    @Value("${auth.endPoint.instroSpec}")
    private String INTRO_SPEC_END_POINT;

    @Value("${auth.endPoint.token}")
    private String TOKEN_END_POINT;

    public AuthFilter( WebClient.Builder webClientBuilder, ObjectMapper objectMapper )
    {
        super(Config.class);
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
    }

    @Data
    public static class Config{}

    @Override
    public GatewayFilter apply(Config config)
    {
        return ( ( exchange, chain ) ->
        {
            ServerHttpRequest request = exchange.getRequest();


            if( !request.getHeaders().containsKey( CHINCHINNE_AUTHORIZATION ) )
            {
                return unAuthorizedHandler(exchange, ErrorCode.NOT_FOUND_TOKEN);
            }
            else
            {
                Authorization auth;

                try
                {
                     auth = objectMapper.readValue(request.getHeaders().get(CHINCHINNE_AUTHORIZATION).get(0), Authorization.class);

                } catch (JsonProcessingException e)
                {
                    throw new RuntimeException(e);
                }

                MultiValueMap<String, String> accessParams = new LinkedMultiValueMap<>();
                accessParams.add("token", auth.getAccessToken());
                accessParams.add("client_id", CLIENT_ID);
                accessParams.add("client_secret", CLIENT_SECRET);

                // 여러개 한번에 날려서 처리하는 방법 확인

                // accessToken 확인
                Mono<IntroSpecResponse> access = webClientBuilder.baseUrl(INTRO_SPEC_END_POINT)
                                                                .build()
                                                                .method(HttpMethod.POST)
                                                                .contentType( MediaType.APPLICATION_FORM_URLENCODED )
                                                                .body(BodyInserters.fromFormData(accessParams))
                                                                .retrieve()
                                                                .bodyToMono( IntroSpecResponse.class );

                MultiValueMap<String, String> refreshParams = new LinkedMultiValueMap<>();
                refreshParams.add("token", auth.getRefreshToken());
                refreshParams.add("client_id", CLIENT_ID);
                refreshParams.add("client_secret", CLIENT_SECRET);

                // refreshToken 확인
                Mono<IntroSpecResponse> refresh = webClientBuilder.baseUrl(INTRO_SPEC_END_POINT)
                                                                .build()
                                                                .method(HttpMethod.POST)
                                                                .contentType( MediaType.APPLICATION_FORM_URLENCODED )
                                                                .body(BodyInserters.fromFormData(refreshParams))
                                                                .retrieve()
                                                                .bodyToMono( IntroSpecResponse.class );

                MultiValueMap<String, String> reTokenParams = new LinkedMultiValueMap<>();
                reTokenParams.add("grant_type", GrantType.REFRESH_TOKEN.name());
                reTokenParams.add("client_id", CLIENT_ID);
                reTokenParams.add("client_secret", CLIENT_SECRET);
                reTokenParams.add("refresh_token", auth.getRefreshToken());

                // 토큰 재요청
                Mono<TokenResponse> reToken =  webClientBuilder.baseUrl(TOKEN_END_POINT)
                                                                .build()
                                                                .method(HttpMethod.POST)
                                                                .contentType( MediaType.APPLICATION_FORM_URLENCODED )
                                                                .body(BodyInserters.fromFormData(reTokenParams))
                                                                .retrieve()
                                                                .bodyToMono( TokenResponse.class );

                 return Mono.zip(access, refresh).flatMap( res ->
                                                {
                                                    IntroSpecResponse accessResponse = res.getT1();
                                                    IntroSpecResponse refreshResponse = res.getT2();

                                                    // accessToken 활성화일 시, 다음 요청 처리
                                                    if( accessResponse.isActive() )
                                                    {
                                                        return chain.filter(exchange);
                                                    }
                                                    // accessToken 비활성화일 시, refreshToken 결과 확인
                                                    else
                                                    {
                                                        ServerHttpResponse response = exchange.getResponse();

                                                        // refreshToken 활성화 일 시, accessToken 재발급 처리
                                                        if( refreshResponse.isActive() )
                                                        {
                                                            return reToken.flatMap( resToken ->
                                                            {
                                                                // accessToken만 응답헤더에 저장
                                                                response.getHeaders().add("reToken", resToken.getAccessToken());

                                                                return chain.filter(exchange);
                                                            });
                                                        }
                                                        // refreshToken 비활성화 일 시, 토큰 만료 오류 처리
                                                        else
                                                        {
                                                            return unAuthorizedHandler(exchange, ErrorCode.EXPIRE_TOKEN);
                                                        }
                                                    }
                                                })
                                                .onErrorResume( e ->
                                                {
                                                    return exceptionHandler(exchange, ((WebClientResponseException) e));
                                                });
            }
        });
    }

    private Mono<Void> unAuthorizedHandler(ServerWebExchange exchange, ErrorCode errorCode)
    {
        ServerHttpResponse res = (ServerHttpResponse) exchange.getResponse();

        byte[] bytes;
        res.setStatusCode( HttpStatus.UNAUTHORIZED ) ;
        res.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        try
        {
            bytes = objectMapper.writeValueAsBytes
            (
                ErrorResponse.builder().status(errorCode.getCode())
                                        .error(errorCode.name())
                                        .code(errorCode.getCode())
                                        .message(errorCode.getDetail())
                                        .build()
            );
        }
        catch (JsonProcessingException e)
        {
            throw new RuntimeException(e);
        }

        DataBuffer buffer = res.bufferFactory().wrap(bytes);

        return res.writeWith(Mono.just(buffer));
    }

    private Mono<Void> exceptionHandler(ServerWebExchange exchange, WebClientResponseException exception)
    {
        ServerHttpResponse res = (ServerHttpResponse) exchange.getResponse();

        byte[] bytes;
        res.setStatusCode(HttpStatus.UNAUTHORIZED);
        res.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        DataBuffer buffer = res.bufferFactory().wrap(((WebClientResponseException) exception).getResponseBodyAsByteArray());

        return res.writeWith(Mono.just(buffer)) ;
    }
}
