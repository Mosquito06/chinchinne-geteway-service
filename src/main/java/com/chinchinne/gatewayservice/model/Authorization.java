package com.chinchinne.gatewayservice.model;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class Authorization
{
    private String accessToken;
    private String refreshToken;
}
