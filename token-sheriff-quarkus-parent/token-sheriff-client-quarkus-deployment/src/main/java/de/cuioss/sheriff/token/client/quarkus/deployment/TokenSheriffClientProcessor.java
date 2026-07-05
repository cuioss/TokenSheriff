/*
 * Copyright © 2025 CUI-OpenSource-Software (info@cuioss.de)
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
package de.cuioss.sheriff.token.client.quarkus.deployment;

import de.cuioss.tools.logging.CuiLogger;
import de.cuioss.tools.logging.LogRecord;
import de.cuioss.tools.logging.LogRecordModel;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

/**
 * Build-time processor for the Token-Sheriff client Quarkus extension.
 * <p>
 * In this wired, empty skeleton (Plan 05) it registers the {@code token-sheriff-client} feature so the
 * extension assembles against the reactor. Build steps for the client flows (reflection, CDI beans,
 * native-image resources, DevUI) are added alongside those flows in later increments (Plan 06).
 */
public class TokenSheriffClientProcessor {

    /**
     * The feature name for the Token-Sheriff client extension.
     */
    private static final String FEATURE = "token-sheriff-client";

    /**
     * Logger for build-time processing.
     */
    private static final CuiLogger LOGGER = new CuiLogger(TokenSheriffClientProcessor.class);

    /**
     * LogRecord for feature registration.
     */
    private static final LogRecord TOKEN_SHERIFF_CLIENT_FEATURE_REGISTERED = LogRecordModel.builder()
            .template("Token-Sheriff client feature registered")
            .prefix("TokenSheriffClient_Q_D")
            .identifier(1)
            .build();

    /**
     * Register the Token-Sheriff client feature.
     *
     * @return a {@link FeatureBuildItem} for the {@code token-sheriff-client} feature
     */
    @BuildStep
    public FeatureBuildItem feature() {
        LOGGER.info(TOKEN_SHERIFF_CLIENT_FEATURE_REGISTERED);
        return new FeatureBuildItem(FEATURE);
    }
}
