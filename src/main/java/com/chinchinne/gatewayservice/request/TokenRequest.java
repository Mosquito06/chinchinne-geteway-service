package com.chinchinne.gatewayservice.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.*;

@Data
@NoArgsConstructor
@JsonIgnoreProperties( ignoreUnknown = true )
@JsonNaming(PropertyNamingStrategy.LowerCaseStrategy.class)
//@JsonNaming( value = PropertyNamingStrategies.LowerCamelCaseStrategy.class )
public class TokenRequest
{
    private final String CLIENT_ID = "legacy-oauth-client";
    private final String CLIENT_SECRET = "secret";
    private String scope;
    private String userName;
    private String password;
    private GrantType grantType;
}
