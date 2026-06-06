package com.fee.app.schoolfeeapp.auth.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KeycloakConfigResponse {
    private String authServerUrl;
    private String realm;
    private String clientId;
    private String tokenEndpoint;
    private String logoutEndpoint;
    
    public static KeycloakConfigResponse create() {
        return KeycloakConfigResponse.builder()
                .authServerUrl("http://localhost:8081")
                .realm("schoolfee")
                .clientId("schoolfee-web")
                .tokenEndpoint("/realms/schoolfee/protocol/openid-connect/token")
                .logoutEndpoint("/realms/schoolfee/protocol/openid-connect/logout")
                .build();
    }
}