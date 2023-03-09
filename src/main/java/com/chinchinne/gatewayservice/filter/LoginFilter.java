package com.chinchinne.gatewayservice.filter;

import com.chinchinne.gatewayservice.request.GrantType;
import com.chinchinne.gatewayservice.request.TokenRequest;
import com.chinchinne.gatewayservice.response.TokenResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
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
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class LoginFilter extends AbstractGatewayFilterFactory<LoginFilter.Config>
{
    private WebClient.Builder webClientBuilder;
    private ObjectMapper objectMapper;

    public LoginFilter( WebClient.Builder webClientBuilder, ObjectMapper objectMapper )
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

            return DataBufferUtils.join( request.getBody() ).flatMap( dataBuffer ->
            {
                byte[] requestBodyByteArray = new byte[dataBuffer.readableByteCount()];
                dataBuffer.read(requestBodyByteArray);
                String requestBodyString = new String(requestBodyByteArray, StandardCharsets.UTF_8);
                DataBufferUtils.release(dataBuffer);
                TokenRequest tokenRequest;

                try
                {
                    tokenRequest = objectMapper.readValue(requestBodyString, TokenRequest.class);
                }
                catch (JsonProcessingException e)
                {
                    throw new RuntimeException(e);
                }

                MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
                params.add("grant_type", GrantType.AUTHORIZATION_CODE.name());
                params.add("client_id", tokenRequest.getCLIENT_ID());
                params.add("client_secret", tokenRequest.getCLIENT_SECRET());
                params.add("username", tokenRequest.getUserName());
                params.add("password", tokenRequest.getPassword());

                return webClientBuilder.baseUrl("http://auth-service/oauth/token")
                                        .build()
                                        .method(HttpMethod.POST)
                                        .contentType( MediaType.APPLICATION_FORM_URLENCODED )
                                        .body(BodyInserters.fromFormData(params))
                                        .retrieve()
                                        .bodyToMono(TokenResponse.class)

                                        .flatMap( tokenresponse ->
                                        {
                                            ServerHttpResponse res = (ServerHttpResponse) exchange.getResponse();

                                            byte[] bytes;
                                            res.setStatusCode(HttpStatus.OK);
                                            res.getHeaders().setContentType(MediaType.APPLICATION_JSON);

                                            try
                                            {
                                                bytes = objectMapper.writeValueAsBytes(tokenresponse);
                                            }
                                            catch (JsonProcessingException e)
                                            {
                                                throw new RuntimeException(e);
                                            }

                                            DataBuffer buffer = res.bufferFactory().wrap(bytes);

                                            return res.writeWith(Mono.just(buffer)) ;
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
            });
        });
    }
}
