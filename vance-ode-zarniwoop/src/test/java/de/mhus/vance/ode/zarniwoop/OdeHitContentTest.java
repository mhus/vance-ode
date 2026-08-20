/*
 * Copyright 2026 Mike Hummel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.mhus.vance.ode.zarniwoop;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@code sizeBytes} is what a caller budgets against before fetching a body, so
 * it has to be bytes. {@code String.length()} counts UTF-16 chars and
 * under-reports everything that is not plain ASCII.
 */
class OdeHitContentTest {

    @Test
    void embedded_sizeIsMeasuredInUtf8Bytes() {
        // Five chars, seven UTF-8 bytes — two of the umlauts cost two each.
        assertThat(OdeHitContent.embedded("c1", "Größe").sizeBytes()).isEqualTo(7);
    }

    @Test
    void embedded_asciiIsUnchanged() {
        assertThat(OdeHitContent.embedded("c1", "plain").sizeBytes()).isEqualTo(5);
    }

    @Test
    void embedded_withoutText_isZero() {
        assertThat(OdeHitContent.embedded("c1", null).sizeBytes()).isZero();
    }
}
