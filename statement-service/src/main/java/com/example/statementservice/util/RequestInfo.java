package com.example.statementservice.util;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RequestInfo {
    private String clientIp;
    private String userAgent;
    private String performedBy;
}
