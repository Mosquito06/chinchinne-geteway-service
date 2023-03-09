package com.chinchinne.gatewayservice.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Data
@NoArgsConstructor
@JsonIgnoreProperties( ignoreUnknown = true )
@JsonNaming(PropertyNamingStrategy.LowerCaseStrategy.class)
//@JsonNaming( value = PropertyNamingStrategies.LowerCamelCaseStrategy.class )
public class TokenRequest
{
    @Value("${auth.clientId}")
    private String CLIENT_ID;

    @Value("${auth.secret}")
    private String CLIENT_SECRET;

    private String scope;
    private String userName;
    private String password;
    private GrantType grantType;
}
