package com.rabbitmq.client;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import java.io.IOException;

/**
 * Ready-to-use instances and builder for {@link ConnectionPostProcessor}.
 * <p>
 * Note {@link ConnectionPostProcessor}s can be combined with
 * {@link AbstractConnectionPostProcessor#andThen(ConnectionPostProcessor)}.
 *
 * @since 4.8.0
 */
public abstract class ConnectionPostProcessors {

    /**
     * Perform hostname verification for TLS connections.
     * <p>
     * Enforcing hostname verification this way is relevant for Java 6.
     * If using Java 7 or more, you'd better off using {@link SocketConfigurators}
     * or {@link SslEngineConfigurators}, depending on whether you're using
     * blocking IO or non-blocking IO, respectively.
     *
     * @param verifier the {@link HostnameVerifier} to use
     * @return
     */
    public static AbstractConnectionPostProcessor hostnameVerificationConnectionPostProcessor(HostnameVerifier verifier) {
        return new HostnameVerificationConnectionPostProcessor(verifier);
    }

    /**
     * Builder to configure and create a {@link ConnectionPostProcessor} instance.
     *
     * @return
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Perform hostname verification for TLS connections.
     */
    private static class HostnameVerificationConnectionPostProcessor extends AbstractConnectionPostProcessor {

        private final HostnameVerifier verifier;

        private HostnameVerificationConnectionPostProcessor(HostnameVerifier verifier) {
            this.verifier = verifier;
        }

        @Override
        public void postProcess(ConnectionContext context) throws IOException {
            SSLSession sslSession = context.getSslSession();
            if (sslSession == null && context.getSocket() instanceof SSLSocket) {
                sslSession = ((SSLSocket) context.getSocket()).getSession();
            }
            if (sslSession != null) {
                String hostname = context.getAddress().getHost();
                if (!verifier.verify(hostname, sslSession)) {
                    throw new SSLHandshakeException("Hostname " + hostname + " does not match "
                        + "TLS certificate SAN or CN");
                }
            }
        }
    }

    public abstract static class AbstractConnectionPostProcessor implements ConnectionPostProcessor {

        /**
         * Returns a composed processor that performs, in sequence, this
         * operation followed by the {@code after} operation.
         *
         * @param after the operation to perform after this operation
         * @return a composed processor that performs in sequence this
         * operation followed by the {@code after} operation
         * @throws NullPointerException if {@code after} is null
         */
        public AbstractConnectionPostProcessor andThen(final ConnectionPostProcessor after) {
            if (after == null) {
                throw new NullPointerException();
            }
            return new AbstractConnectionPostProcessor() {

                @Override
                public void postProcess(ConnectionContext t) throws IOException {
                    AbstractConnectionPostProcessor.this.postProcess(t);
                    after.postProcess(t);
                }
            };
        }
    }

    public static class Builder {

        private AbstractConnectionPostProcessor postProcessor = new AbstractConnectionPostProcessor() {

            @Override
            public void postProcess(ConnectionContext context) {

            }
        };

        /**
         * Enable hostname verification enforced by the provided {@link HostnameVerifier}.
         *
         * @param hostnameVerifier
         * @return
         */
        public Builder enableHostnameVerification(HostnameVerifier hostnameVerifier) {
            this.postProcessor = this.postProcessor.andThen(hostnameVerificationConnectionPostProcessor(hostnameVerifier));
            return this;
        }

        /**
         * Add an extra post-processing step.
         *
         * @param connectionPostProcessor
         * @return
         */
        public Builder add(ConnectionPostProcessor connectionPostProcessor) {
            this.postProcessor = this.postProcessor.andThen(connectionPostProcessor);
            return this;
        }

        /**
         * Return the configured {@link ConnectionPostProcessor}.
         *
         * @return
         */
        public ConnectionPostProcessor build() {
            return this.postProcessor;
        }
    }
}
