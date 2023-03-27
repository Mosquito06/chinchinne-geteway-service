package com.chinchinne.gatewayservice.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode
{
     NOT_FOUND_TOKEN(1000, "허가되지 않은 접근입니다.")
    ,EXPIRE_TOKEN(1001, "로그인 시간이 만료되었습니다.");

    private final int code;
    private final String detail;
}
