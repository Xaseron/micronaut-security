/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.security.token.basicauth;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.context.ServerRequestContext;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.authentication.AuthenticationResponse;
import io.micronaut.security.authentication.AuthenticationUserDetailsAdapter;
import io.micronaut.security.authentication.Authenticator;
import io.micronaut.security.authentication.UserDetails;
import io.micronaut.security.authentication.UsernamePasswordCredentials;
import io.micronaut.security.token.config.TokenConfiguration;
import io.micronaut.security.token.validator.TokenValidator;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

/**
 * Basic Auth Token Validator.
 *
 * @author Sergio del Amo
 * @since 1.0
 * @deprecated Will be changed to be an authentication fetcher in a future release.
 */
@Requires(property = BasicAuthTokenReaderConfigurationProperties.PREFIX + ".enabled", notEquals = StringUtils.FALSE)
@Singleton
@Deprecated
public class BasicAuthTokenValidator implements TokenValidator {

    /**
     * The order of the TokenValidator.
     */
    public static final Integer ORDER = 0;

    private static final Logger LOG = LoggerFactory.getLogger(BasicAuthTokenValidator.class);

    protected final Authenticator authenticator;

    private final String rolesKeyName;

    /**
     * @deprecated Use {@link #BasicAuthTokenValidator(Authenticator, TokenConfiguration)}
     * @param authenticator The Authenticator
     */
    @Deprecated
    public BasicAuthTokenValidator(Authenticator authenticator) {
        this.authenticator = authenticator;
        this.rolesKeyName = TokenConfiguration.DEFAULT_ROLES_NAME;
    }

    /**
     * @param authenticator The Authenticator
     * @param tokenConfiguration The Token configuration.
     */
    @Inject
    public BasicAuthTokenValidator(Authenticator authenticator,
                                   TokenConfiguration tokenConfiguration) {
        this.authenticator = authenticator;
        this.rolesKeyName = tokenConfiguration.getRolesName();
    }

    @Override
    public Publisher<Authentication> validateToken(String encodedToken) {
        Optional<UsernamePasswordCredentials> creds = credsFromEncodedToken(encodedToken);
        if (creds.isPresent()) {
            HttpRequest<?> request = ServerRequestContext.currentRequest().orElse(null);
            Flowable<AuthenticationResponse> authenticationResponse = Flowable.fromPublisher(authenticator.authenticate(request, creds.get()));

            return authenticationResponse.switchMap(response -> {
                if (response.isAuthenticated()) {
                    UserDetails userDetails = (UserDetails) response;
                    return Flowable.just(new AuthenticationUserDetailsAdapter(userDetails, rolesKeyName));
                } else {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Could not authenticate {}", creds.get().getUsername());
                    }
                    return Flowable.empty();
                }

            });
        }
        return Flowable.empty();
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    private Optional<UsernamePasswordCredentials> credsFromEncodedToken(String encodedToken) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(encodedToken);
        } catch (IllegalArgumentException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Error while trying to Base 64 decode: {}", encodedToken);
            }
            return Optional.empty();
        }

        String token;
        token = new String(decoded, StandardCharsets.UTF_8);

        final int delim = token.indexOf(":");
        if (delim < 0) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Bad format of the basic auth header - Delimiter : not found");
            }
            return Optional.empty();
        }

        final String username = token.substring(0, delim);
        final String password = token.substring(delim + 1);
        return Optional.of(new UsernamePasswordCredentials(username, password));
    }
}
