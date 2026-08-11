package com.example.statementservice.util;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;

public class CommonUtil {

    public static final String HTTP = "http";
    public static final String HTTPS = "https";

    public static URI buildProblemDetailTypeURI(HttpServletRequest request, String contextPath) {
        var scheme = request.getScheme();
        var serverName = request.getServerName();
        int serverPort = request.getServerPort();

        var baseUrl = new StringBuilder(scheme).append("://").append(serverName);

        if (!((scheme.equals(HTTP) && serverPort == 80) || (scheme.equals(HTTPS) && serverPort == 443))) {
            baseUrl.append(":").append(serverPort);
        }

        if (contextPath != null && !contextPath.isEmpty()) {
            baseUrl.append(contextPath);
        }

        return URI.create(baseUrl.toString());
    }
}
