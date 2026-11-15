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
package de.cuioss.sheriff.token.client.flow;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The identity refusal carrier: an {@link IllegalStateException} that keeps the refusal's message and
 * cause, carries the redemption it was raised under, and never serializes the rotated successor.
 */
@EnableGeneratorController
@DisplayName("RedeemedIdentityRefusalException carries an identity refusal after redemption")
class RedeemedIdentityRefusalExceptionTest {

    @Test
    @DisplayName("Should keep the refusal's message and set the refusal as cause")
    void shouldKeepMessageAndCause() {
        var refusal = new IllegalStateException(Generators.letterStrings(10, 30).next());

        var carrier = new RedeemedIdentityRefusalException(refusal, RefreshRedemption.notRotated());

        assertAll("the carrier is the refusal, seen through IllegalStateException",
                () -> assertEquals(refusal.getMessage(), carrier.getMessage(), "message is kept"),
                () -> assertSame(refusal, carrier.getCause(), "the refusal is the cause"),
                () -> assertInstanceOf(IllegalStateException.class, carrier,
                        "existing catch (IllegalStateException) blocks keep catching it"));
    }

    @Test
    @DisplayName("Should report the carried redemption and classify as REDEEMED")
    void shouldReportCarriedRedemption() {
        var redemption = RefreshRedemption.rotated(Generators.letterStrings(20, 40).next());

        var carrier = new RedeemedIdentityRefusalException(new IllegalStateException("refused"), redemption);
        RefreshFailureClassification classification = RefreshFlow.classify(carrier);

        assertAll("the redemption is read back unchanged",
                () -> assertEquals(redemption, carrier.redemption(), "redemption() returns the carried state"),
                () -> assertEquals(RefreshFailureClassification.Kind.REDEEMED, classification.kind(),
                        "the classifier reads the carrier as post-redemption"),
                () -> assertEquals(redemption, classification.redemption(),
                        "the classifier hands back the carried redemption"));
    }

    @Test
    @DisplayName("Should degrade to rotationUnknown after serialization without carrying the successor")
    void shouldDegradeAfterSerialization() throws IOException, ClassNotFoundException {
        String successor = Generators.letterStrings(30, 50).next();
        var carrier = new RedeemedIdentityRefusalException(new IllegalStateException("refused"),
                RefreshRedemption.rotated(successor));

        byte[] serialized = serialize(carrier);
        var restored = (RedeemedIdentityRefusalException) deserialize(serialized);

        assertAll("the live successor never crosses a serialization boundary",
                () -> assertFalse(new String(serialized, StandardCharsets.ISO_8859_1).contains(successor),
                        "the serialized form must not contain the rotated refresh token"),
                () -> assertEquals(RefreshRedemption.rotationUnknown(), restored.redemption(),
                        "a deserialized carrier fails closed: still redeemed, nothing to revoke"),
                () -> assertEquals(carrier.getMessage(), restored.getMessage(), "the message survives"));
    }

    @Test
    @DisplayName("Should reject null arguments")
    void shouldRejectNullArguments() {
        var refusal = new IllegalStateException("refused");
        var redemption = RefreshRedemption.notRotated();

        assertAll("null-guards",
                () -> assertThrows(NullPointerException.class,
                        () -> new RedeemedIdentityRefusalException(null, redemption)),
                () -> assertThrows(NullPointerException.class,
                        () -> new RedeemedIdentityRefusalException(refusal, null)));
    }

    private static byte[] serialize(Object value) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
        }
        return bytes.toByteArray();
    }

    private static Object deserialize(byte[] serialized) throws IOException, ClassNotFoundException {
        try (var in = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            return in.readObject();
        }
    }
}
