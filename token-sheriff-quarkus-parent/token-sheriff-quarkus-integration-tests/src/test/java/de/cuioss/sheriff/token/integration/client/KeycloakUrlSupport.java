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
package de.cuioss.sheriff.token.integration.client;

/**
 * The single public Keycloak authority the client integration tests address.
 * <p>
 * The shared Keycloak runs with the static hostname {@code KC_HOSTNAME=https://localhost:1443}
 * (see {@code docker-compose.yml}) and the realm imports carry no {@code frontendUrl}. Keycloak
 * therefore emits this one authority in every URL it generates — the {@code issuer} in the discovery
 * document, the {@code login-actions} form-action URL on the login page and the discovery-advertised
 * endpoints such as the {@code pushed_authorization_request_endpoint} — whichever side of the port
 * mapping a request arrives from.
 * <p>
 * The {@code *IT} tests run on the CI runner <em>outside</em> the Docker network, where the container
 * is reachable through the host port mapping {@code 1443:8443}, i.e. at {@code https://localhost:1443}.
 * A test can therefore follow a Keycloak-emitted URL as it is. The application container, inside the
 * Docker network, fetches key material over the Docker-internal {@code https://keycloak:8443} but
 * validates tokens against the same public issuer.
 */
final class KeycloakUrlSupport {

    /**
     * Public Keycloak base — the static {@code KC_HOSTNAME}, reachable from the CI runner through the
     * host port mapping {@code 1443:8443} from {@code docker-compose.yml}.
     */
    static final String EXTERNAL_BASE = "https://localhost:1443";

    private KeycloakUrlSupport() {
        // utility class
    }
}
