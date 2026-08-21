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
package de.mhus.vance.ode.kit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The convenient path: a kit that is just files. */
class StaticKitSourceTest {

    private static final OdeKitBuildRequest REQUEST =
            new OdeKitBuildRequest("acme-crm", "acme-prod", "acme", "sales", "https://host.example");

    @Test
    void fromClasspath_findsThePackagedKit() {
        KitSource source = StaticKitSource.fromClasspath("acme-crm", "kits/acme-crm");

        assertThat(source.build(REQUEST).files())
                .containsKeys(OdeKitBundle.DESCRIPTOR, "tools/crm.yaml");
    }

    @Test
    void fromClasspath_declaresAContentRevision() {
        KitSource source = StaticKitSource.fromClasspath("acme-crm", "kits/acme-crm");

        OdeKitDeclaration declaration = source.declare();
        assertThat(declaration.id()).isEqualTo("acme-crm");
        assertThat(declaration.revision())
                .isEqualTo(KitTreeHash.of(source.build(REQUEST).files()));
    }

    @Test
    void fromClasspath_missingBase_saysSoRatherThanServingNothing() {
        // An empty bundle would surface at the far end as "delivered without a
        // descriptor", which sends whoever debugs it to the wrong end.
        assertThatThrownBy(() -> StaticKitSource
                .fromClasspath("ghost", "kits/does-not-exist").declare())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is the kit packaged?");
    }

    @Test
    void fromDirectory_picksUpAnEditWithoutRestart(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("kit.yaml"), "name: local\ndescription: d\n");
        KitSource source = StaticKitSource.fromDirectory("local", dir);
        String before = source.declare().revision();

        Files.writeString(dir.resolve("kit.yaml"), "name: local\ndescription: changed\n");

        // A directory is where someone is editing. "Why is my change not
        // showing up" costs more than re-reading a few kilobytes.
        assertThat(source.declare().revision()).isNotEqualTo(before);
    }

    @Test
    void revision_ignoresCollectionOrder(@TempDir Path a, @TempDir Path b) throws Exception {
        Files.writeString(a.resolve("kit.yaml"), "name: k\ndescription: d\n");
        Files.writeString(a.resolve("z.md"), "z\n");
        Files.writeString(b.resolve("z.md"), "z\n");
        Files.writeString(b.resolve("kit.yaml"), "name: k\ndescription: d\n");

        assertThat(StaticKitSource.fromDirectory("k", a).declare().revision())
                .isEqualTo(StaticKitSource.fromDirectory("k", b).declare().revision());
    }

    @Test
    void revision_changesWithContent(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("kit.yaml"), "name: k\ndescription: d\n");
        String before = StaticKitSource.fromDirectory("k", dir).declare().revision();

        Files.writeString(dir.resolve("extra.md"), "new file\n");

        assertThat(StaticKitSource.fromDirectory("k", dir).declare().revision())
                .isNotEqualTo(before);
    }

    @Test
    void bundle_withoutDescriptor_isRefusedHere(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("tools.yaml"), "x\n");

        assertThatThrownBy(() -> StaticKitSource.fromDirectory("k", dir).build(REQUEST))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(OdeKitBundle.DESCRIPTOR);
    }

    @Test
    void bundle_escapingPath_isRefusedHere() {
        assertThatThrownBy(() -> new OdeKitBundle(java.util.Map.of(
                OdeKitBundle.DESCRIPTOR, "name: k\ndescription: d\n".getBytes(StandardCharsets.UTF_8),
                "../escape.md", "no\n".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be relative");
    }

    @Test
    void declaration_withoutRevision_isRefused() {
        assertThatThrownBy(() -> new OdeKitDeclaration("k", "1.0", "  ", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must declare a revision");
    }
}
