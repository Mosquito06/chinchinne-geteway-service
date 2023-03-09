package com.chinchinne.gatewayservice.filter;

import com.chinchinne.gatewayservice.model.ErrorCode;
import com.chinchinne.gatewayservice.model.ErrorResponse;
import com.chinchinne.gatewayservice.response.IntroSpecResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
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

    public AuthFilter( WebClient.Builder webClientBuilder, ObjectMapper objectMapper )
    {
        super(Config.class);
        this.webClientBuilder = webClientBuilder;
        this.objectMapper = objectMapper;
    }

    @Data
    public static class Config
    {

    }

    @Override
    public GatewayFilter apply(Config config)
    {
        return ( ( exchange, chain ) ->
        {
            ServerHttpRequest request = exchange.getRequest();


            if( !request.getHeaders().containsKey( HttpHeaders.AUTHORIZATION ) )
            {
                return handleUnAuthorized(exchange, ErrorCode.NOT_FOUND_TOKEN);
            }
            else
            {
                String auth = request.getHeaders().get(HttpHeaders.AUTHORIZATION).get(0);

                MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
                params.add("token", auth);
                params.add("client_id", CLIENT_ID);
                params.add("client_secret", CLIENT_SECRET);

                return webClientBuilder.baseUrl("http://auth-service/oauth2/introspect")
                                        .build()
                                        .method(HttpMethod.POST)
                                        .contentType( MediaType.APPLICATION_FORM_URLENCODED )
                                        .body(BodyInserters.fromFormData(params))
                                        .retrieve()
                                        .bodyToMono( IntroSpecResponse.class )
                                        .flatMap( introSpecResponse ->
                                        {
                                            if( introSpecResponse.isActive() )
                                            {
                                                return chain.filter(exchange);
                                            }
                                            else
                                            {
                                                return handleUnAuthorized(exchange, ErrorCode.EXPIRE_TOKEN);
                                            }
                                        })
                                        .onErrorResume( e ->
                                        {
                                            ServerHttpResponse res = (ServerHttpResponse) exchange.getResponse();

                                            byte[] bytes;
                                            res.setStatusCode(HttpStatus.UNAUTHORIZED);
                                            res.getHeaders().setContentType(MediaType.APPLICATION_JSON);

                                            DataBuffer buffer = res.bufferFactory().wrap(((WebClientResponseException) e).getResponseBodyAsByteArray());

                                            return res.writeWith(Mono.just(buffer)) ;
                                        });
            }
        });
    }

    private Mono<Void> handleUnAuthorized(ServerWebExchange exchange, ErrorCode errorCode)
    {
        ServerHttpResponse res = (ServerHttpResponse) exchange.getResponse();

        byte[] bytes;
        res.setStatusCode( HttpStatus.UNAUTHORIZED ) ;
        res.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        try
        {
            bytes = objectMapper.writeValueAsBytes
            (
                ErrorResponse.builder().status(errorCode.getHttpStatus().value())
                                        .error(errorCode.getHttpStatus().name())
                                        .code(errorCode.name())
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
}
