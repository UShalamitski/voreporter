package com.epam.voreporter.integration;

import com.versionone.apiclient.V1Connector;
import com.versionone.apiclient.exceptions.V1Exception;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.net.MalformedURLException;

/*
 * Class that establishes new connection to V1 services.
 * <p/>
 * Date: 07/10/19
 *
 * @author Uladzislau Shalamitski
 */
@Component
public class V1Connection {

    private V1Connector connector;

    @Value("${v1.endpoint}")
    private String endpoint;
    @Value("${v1.token}")
    private String token;

    @PostConstruct
    public void init() {
        try {
            connector = V1Connector
                    .withInstanceUrl(endpoint)
                    .withUserAgentHeader("AppName", "1.0")
                    .withAccessToken(token)
                    .build();
        } catch (V1Exception | MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    public V1Connector getConnector() {
        return connector;
    }
}
