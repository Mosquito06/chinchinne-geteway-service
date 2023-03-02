package com.chinchinne.gatewayservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthDto
{
    private String tokenType;
    private String accessToken;
    private String refreshToken;
    private String scope;
    private String idToken;
    private Long expiressIn;
}
