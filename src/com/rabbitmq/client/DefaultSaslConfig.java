package com.rabbitmq.client;

import javax.security.auth.callback.CallbackHandler;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;
import java.util.Map;

/**
 *
 */
public class DefaultSaslConfig implements SaslConfig {
    private ConnectionFactory factory;
    private String authorizationId;
    private Map<String,?> mechanismProperties;
    private CallbackHandler callbackHandler;

    public DefaultSaslConfig(ConnectionFactory factory) {
        this.factory = factory;
        callbackHandler = new UsernamePasswordCallbackHandler(factory);
    }

    public void setAuthorizationId(String authorizationId) {
        this.authorizationId = authorizationId;
    }

    public void setMechanismProperties(Map<String, ?> mechanismProperties) {
        this.mechanismProperties = mechanismProperties;
    }

    public void setCallbackHandler(CallbackHandler callbackHandler) {
        this.callbackHandler = callbackHandler;
    }

    public SaslClient getSaslClient(String[] mechanisms) throws SaslException {
        return Sasl.createSaslClient(mechanisms, authorizationId, "AMQP",
              factory.getHost(), mechanismProperties, callbackHandler);
    }
}
