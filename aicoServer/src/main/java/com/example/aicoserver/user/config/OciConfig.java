package com.example.aicoserver.user.config;

import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OciConfig {

    @Bean
    public ObjectStorageClient objectStorageClient() throws Exception {
        ConfigFileAuthenticationDetailsProvider provider =
                new ConfigFileAuthenticationDetailsProvider("/home/opc/.oci/config", "DEFAULT");
        return ObjectStorageClient.builder().build(provider);
    }
}
