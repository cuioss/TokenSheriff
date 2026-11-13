/*
 * Copyright © 2025-present CUI-OpenSource-Software (info@cuioss.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.cuioss.sheriff.token.validation;

import de.cuioss.sheriff.token.commons.transport.JwksType;
import de.cuioss.sheriff.token.commons.transport.LoaderStatus;
import de.cuioss.sheriff.token.validation.jwks.JwksLoader;

import java.util.Objects;

/**
 * A read-only view of one enabled issuer of a {@link TokenValidator}.
 * <p>
 * The view names the issuer and its JWKS type and reads the current {@link LoaderStatus} of the issuer's
 * JWKS loader on every call to {@link #loaderStatus()}. Reading the status never fetches keys. The view
 * exposes neither the {@link IssuerConfig} nor the loader, so no caller can close, refresh or replace a
 * loader through it: the loaders stay owned by the validator, which alone closes them in
 * {@link TokenValidator#close()}.
 * <p>
 * This class is immutable and thread-safe.
 *
 * @since 1.0
 * @see TokenValidator#getIssuerStatuses()
 */
public final class IssuerStatusView {

    private final String issuerIdentifier;
    private final JwksType jwksType;
    private final JwksLoader jwksLoader;

    /**
     * Creates a view of the given issuer.
     *
     * @param issuerIdentifier the issuer identifier
     * @param jwksLoader       the issuer's JWKS loader, owned by the validator and only read here
     */
    IssuerStatusView(String issuerIdentifier, JwksLoader jwksLoader) {
        this.issuerIdentifier = Objects.requireNonNull(issuerIdentifier, "issuerIdentifier must not be null");
        this.jwksLoader = Objects.requireNonNull(jwksLoader, "jwksLoader must not be null");
        this.jwksType = jwksLoader.getJwksType();
    }

    /**
     * @return the issuer identifier
     */
    public String issuerIdentifier() {
        return issuerIdentifier;
    }

    /**
     * @return the type of the issuer's JWKS source
     */
    public JwksType jwksType() {
        return jwksType;
    }

    /**
     * Reads the current status of the issuer's JWKS loader without fetching keys.
     *
     * @return the current loader status
     */
    public LoaderStatus loaderStatus() {
        return jwksLoader.getLoaderStatus();
    }

    @Override
    public String toString() {
        return "IssuerStatusView(issuerIdentifier=" + issuerIdentifier + ", jwksType=" + jwksType + ")";
    }
}
