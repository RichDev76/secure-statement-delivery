package com.example.statementservice.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "security.endpoints")
public class SecurityEndpointsProperties {

    private List<String> whitelist = new ArrayList<>();
    private List<String> admin = new ArrayList<>();
    private List<String> audit = new ArrayList<>();
    private List<String> csrfIgnored = new ArrayList<>();
}
