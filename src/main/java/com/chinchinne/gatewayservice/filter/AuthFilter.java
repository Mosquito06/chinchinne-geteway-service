package com.chinchinne.gatewayservice.filter;

import com.chinchinne.gatewayservice.request.TokenRequest;
import com.chinchinne.gatewayservice.request.GrantType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class AuthFilter extends AbstractGatewayFilterFactory<AuthFilter.Config>
{
    // private AuthServiceClient authServiceClient;

    private RestTemplate restTemplate;

    public AuthFilter()
    {
        super(Config.class);
        // this.authServiceClient = authServiceClient;
        //this.restTemplate = restTemplate;
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
                TokenRequest authRequest;

                try
                {
                    authRequest = new ObjectMapper().readValue(requestBodyString, TokenRequest.class);// parse(requestBodyString);


//
//
//                            .exchangeToMono( clientResponse ->
//                            {
//                                System.out.println(clientResponse.toEntity(String.class));
//
//                                return clientResponse.toEntity(String.class);
//                            })
////                            .map( s ->
////                            {
////                                System.out.println(s);
////
////                                return exchange;
////                            })
//                            .onErrorResume( error ->
//                            {
//                                return Mono.error( new RuntimeException( error.getMessage()));
//                            });
//                            //.flatMap( chain :: filter);

                    //String object = restTemplate.postForObject("http://localhost:30028/oauth/token", params, String.class);
                }
                catch (JsonProcessingException e)
                {
                    throw new RuntimeException(e);
                }

                MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
                params.add("grant_type", GrantType.AUTHORIZATION_CODE.name());
                params.add("client_id", authRequest.getCLIENT_ID());
                params.add("client_secret", authRequest.getCLIENT_SECRET());
                params.add("username", authRequest.getUserName());
                params.add("password", authRequest.getPassword());
//                    params.add("redirect_uri", REDIRECT_URI);

// WebClient.create("http://localhost:30026/oauth/token")

                return WebClient.create("lb://auth-service/oauth/token")
                        .method(HttpMethod.POST)
                        .contentType( MediaType.APPLICATION_FORM_URLENCODED )
                        .body(BodyInserters.fromFormData(params))
                        .retrieve()
                        .bodyToMono(String.class)
                        .flatMap( s->
                        {
                            System.out.println(s);
                            return chain.filter(exchange);
                        });

                //return chain.filter(exchange);

//                chain.filter(exchange).then(Mono.fromRunnable( () ->
//                {
//                    //chain.filter(exchange)
//                }));

              //  chain.filter(exchange)
            });



            //return chain.filter(exchange);
        });
    }
}
