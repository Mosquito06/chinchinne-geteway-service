package com.chinchinne.gatewayservice.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode
{
     NOT_FOUND_TOKEN(HttpStatus.UNAUTHORIZED, "허가되지 않은 접근입니다.")
    ,EXPIRE_TOKEN(HttpStatus.UNAUTHORIZED, "로그인 시간이 만료되었습니다.");

    private final HttpStatus httpStatus;
    private final String detail;
}
