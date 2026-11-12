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
package de.cuioss.sheriff.token.validation.logout;

import lombok.EqualsAndHashCode;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * The outcome of {@link LogoutTokenValidator#validate(String)}.
 * <p>
 * An accepted result carries the issuer, the {@code jti} and the {@code sub} and/or {@code sid} that name
 * the session to end. A refused result carries exactly one {@link LogoutTokenCheck} and no claim and no
 * free text. {@link #toString()} names only the outcome, never a claim value.
 * <p>
 * This class is immutable and thread-safe.
 *
 * @since 1.0
 */
@EqualsAndHashCode
public final class LogoutTokenValidationResult {

    private final @Nullable LogoutTokenCheck failedCheck;
    private final @Nullable String issuer;
    private final @Nullable String subject;
    private final @Nullable String sessionId;
    private final @Nullable String jwtId;

    private LogoutTokenValidationResult(@Nullable LogoutTokenCheck failedCheck, @Nullable String issuer,
            @Nullable String subject, @Nullable String sessionId, @Nullable String jwtId) {
        this.failedCheck = failedCheck;
        this.issuer = issuer;
        this.subject = subject;
        this.sessionId = sessionId;
        this.jwtId = jwtId;
    }

    /**
     * Creates an accepted result. The caller has checked that {@code subject} and {@code sessionId} are not
     * both {@code null}.
     *
     * @param issuer    the {@code iss} claim
     * @param subject   the {@code sub} claim, {@code null} when the token carries none
     * @param sessionId the {@code sid} claim, {@code null} when the token carries none
     * @param jwtId     the {@code jti} claim
     * @return the accepted result
     */
    static LogoutTokenValidationResult accepted(String issuer, @Nullable String subject, @Nullable String sessionId,
            String jwtId) {
        return new LogoutTokenValidationResult(null, Objects.requireNonNull(issuer, "issuer must not be null"),
                subject, sessionId, Objects.requireNonNull(jwtId, "jwtId must not be null"));
    }

    /**
     * Creates a refused result.
     *
     * @param failedCheck the check the token failed
     * @return the refused result
     */
    static LogoutTokenValidationResult refused(LogoutTokenCheck failedCheck) {
        return new LogoutTokenValidationResult(Objects.requireNonNull(failedCheck, "failedCheck must not be null"),
                null, null, null, null);
    }

    /**
     * @return {@code true} when the token passed every check
     */
    public boolean isAccepted() {
        return failedCheck == null;
    }

    /**
     * @return the check a refused token failed, empty for an accepted token
     */
    public Optional<LogoutTokenCheck> getFailedCheck() {
        return Optional.ofNullable(failedCheck);
    }

    /**
     * @return the {@code iss} of an accepted token, empty for a refused token
     */
    public Optional<String> getIssuer() {
        return Optional.ofNullable(issuer);
    }

    /**
     * @return the {@code sub} of an accepted token, empty when it carries none or for a refused token
     */
    public Optional<String> getSubject() {
        return Optional.ofNullable(subject);
    }

    /**
     * @return the {@code sid} of an accepted token, empty when it carries none or for a refused token
     */
    public Optional<String> getSessionId() {
        return Optional.ofNullable(sessionId);
    }

    /**
     * @return the {@code jti} of an accepted token, empty for a refused token
     */
    public Optional<String> getJwtId() {
        return Optional.ofNullable(jwtId);
    }

    @Override
    public String toString() {
        return failedCheck == null
                ? "LogoutTokenValidationResult(accepted)"
                : "LogoutTokenValidationResult(refused=" + failedCheck + ")";
    }
}
