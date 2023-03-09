package com.chinchinne.gatewayservice.response;

import com.chinchinne.gatewayservice.model.TokenType;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
//@ToString
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategy.SnakeCaseStrategy.class)
public class TokenResponse implements Serializable
{
    private static final long serialVersionUID = -1L;

    private String uuid;
    private TokenType tokenType;
    private String accessToken;
    private Long expiresIn;
    private String refreshToken;
    private Long refreshExpiresIn;
    private String scope;
    private String idToken;
}
