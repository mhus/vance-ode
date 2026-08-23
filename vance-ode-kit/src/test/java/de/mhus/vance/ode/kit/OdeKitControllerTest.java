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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Here the wire contract <i>is</i> the deliverable, so these tests go through
 * the HTTP layer: paths, status codes, body binding and what the source actually
 * receives. A source implementer breaks on any of those, not on our internal
 * method signatures.
 */
class OdeKitControllerTest {

    private static final String PATH = "/kit";

    /** Records what it was asked for, so the request binding can be asserted. */
    private static final class RecordingKitSource implements KitSource {
        private final String id;
        private final List<OdeKitBuildRequest> requests = new ArrayList<>();

        RecordingKitSource(String id) {
            this.id = id;
        }

        @Override
        public OdeKitDeclaration declare() {
            return new OdeKitDeclaration(id, "1.4.0", "rev-" + id, "the " + id + " kit");
        }

        @Override
        public OdeKitBundle build(OdeKitBuildRequest request) {
            requests.add(request);
            Map<String, byte[]> files = new LinkedHashMap<>();
            files.put("tools/crm.yaml", "baseUrl: {{ accessUrl }}\n".getBytes(StandardCharsets.UTF_8));
            files.put(OdeKitBundle.DESCRIPTOR,
                    ("name: " + id + "\ndescription: d\nrender:\n  - tools/crm.yaml\n")
                            .getBytes(StandardCharsets.UTF_8));
            return new OdeKitBundle(files);
        }
    }

    /** Declares fine, falls over when asked to build. */
    private static final class FailingKitSource implements KitSource {
        private final String id;

        FailingKitSource(String id) {
            this.id = id;
        }

        @Override
        public OdeKitDeclaration declare() {
            return new OdeKitDeclaration(id, null, "rev", null);
        }

        @Override
        public OdeKitBundle build(OdeKitBuildRequest request) {
            throw new IllegalStateException("the index is offline");
        }
    }

    /**
     * Cannot say what it is yet — a directory the operations team creates on
     * first deploy, an index that comes up after the context does.
     */
    private static final class UndeclarableKitSource implements KitSource {

        boolean ready;

        @Override
        public OdeKitDeclaration declare() {
            if (!ready) {
                throw new IllegalArgumentException("not a directory: /var/lib/acme/kit");
            }
            return new OdeKitDeclaration("late", null, "rev-late", null);
        }

        @Override
        public OdeKitBundle build(OdeKitBuildRequest request) {
            return new OdeKitBundle(Map.of(OdeKitBundle.DESCRIPTOR,
                    "name: late\ndescription: d\n".getBytes(StandardCharsets.UTF_8)));
        }
    }

    private RecordingKitSource source;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        source = new RecordingKitSource("acme-crm");
        mvc = MockMvcBuilders
                .standaloneSetup(new OdeKitController(List.of(source), new VanceOdeKitProperties()))
                .build();
    }

    @Test
    void capabilities_listsWhatIsDeclared() throws Exception {
        mvc.perform(get(PATH + "/capabilities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kits[0].id").value("acme-crm"))
                .andExpect(jsonPath("$.kits[0].revision").value("rev-acme-crm"))
                .andExpect(jsonPath("$.kits[0].version").value("1.4.0"));
    }

    @Test
    void capabilities_doesNotBuildAnything() throws Exception {
        mvc.perform(get(PATH + "/capabilities")).andExpect(status().isOk());

        // The whole reason the endpoints are split: a reader asks this on a
        // schedule, and it must not cost what an install costs.
        assertThat(source.requests).isEmpty();
    }

    @Test
    void build_handsOverAZipContainingTheDescriptor() throws Exception {
        byte[] archive = mvc.perform(post(PATH + "/build")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kit":"acme-crm","tenant":"acme","project":"sales",
                                 "instance":"acme-prod","accessUrl":"https://host.example"}"""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(unzip(archive))
                .containsKeys(OdeKitBundle.DESCRIPTOR, "tools/crm.yaml");
    }

    @Test
    void build_passesTheIdentityToTheSource() throws Exception {
        mvc.perform(post(PATH + "/build")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kit":"acme-crm","tenant":"acme","project":"sales",
                                 "instance":"acme-prod","accessUrl":"https://host.example"}"""))
                .andExpect(status().isOk());

        assertThat(source.requests).singleElement().satisfies(r -> {
            assertThat(r.tenant()).isEqualTo("acme");
            assertThat(r.project()).isEqualTo("sales");
            assertThat(r.instance()).isEqualTo("acme-prod");
            assertThat(r.accessUrl()).isEqualTo("https://host.example");
        });
    }

    @Test
    void build_absentInstallId_meansFirstContact() throws Exception {
        mvc.perform(post(PATH + "/build")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kit":"acme-crm","tenant":"acme"}"""))
                .andExpect(status().isOk());

        assertThat(source.requests.getFirst().installId()).isNull();
    }

    @Test
    void build_installId_isPassedThrough() throws Exception {
        mvc.perform(post(PATH + "/build")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kit":"acme-crm","tenant":"acme","installId":"k-42"}"""))
                .andExpect(status().isOk());

        assertThat(source.requests.getFirst().installId()).isEqualTo("k-42");
    }

    @Test
    void build_passesParamsThrough() throws Exception {
        mvc.perform(post(PATH + "/build")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kit":"acme-crm","tenant":"acme",
                                 "params":{"lang":"de","modules":["crm","invoicing"]}}"""))
                .andExpect(status().isOk());

        OdeKitBuildRequest request = source.requests.getFirst();
        assertThat(request.param("lang", "en")).isEqualTo("de");
        assertThat(request.params()).containsKey("modules");
    }

    @Test
    void build_withoutParams_getsAnEmptyMapNotNull() throws Exception {
        mvc.perform(post(PATH + "/build")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kit":"acme-crm","tenant":"acme"}"""))
                .andExpect(status().isOk());

        // An implementation reading params should never need a null check.
        assertThat(source.requests.getFirst().params()).isEmpty();
        assertThat(source.requests.getFirst().param("lang", "en")).isEqualTo("en");
    }

    @Test
    void build_leavesPlaceholdersAlone() throws Exception {
        byte[] archive = mvc.perform(post(PATH + "/build")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kit":"acme-crm","tenant":"acme","accessUrl":"https://host.example"}"""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        // Substituting here would produce a kit tied to this one caller. The
        // reader fills them, which is why accessUrl travels to us at all.
        assertThat(new String(unzip(archive).get("tools/crm.yaml"), StandardCharsets.UTF_8))
                .isEqualTo("baseUrl: {{ accessUrl }}\n");
    }

    @Test
    void build_isReproducible() throws Exception {
        byte[] first = build();
        byte[] second = build();

        // No timestamps, sorted entries: someone comparing two downloads by
        // hand should see a difference only when there is one.
        assertThat(first).isEqualTo(second);
    }

    @Test
    void build_unknownKit_isABadRequestNamingWhatIsServed() throws Exception {
        mvc.perform(post(PATH + "/build")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kit":"nope","tenant":"acme"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("bad_request"))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("acme-crm")));
    }

    @Test
    void build_withoutTenant_isRefused() throws Exception {
        mvc.perform(post(PATH + "/build")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kit":"acme-crm"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void build_malformedBody_isABadRequestNotAServerError() throws Exception {
        mvc.perform(post(PATH + "/build")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void construction_twoSourcesWithOneId_failsAtStartup() {
        // Served by whichever bean came last would make one source silently
        // unreachable.
        assertThatThrownBy(() -> new OdeKitController(
                List.of(new RecordingKitSource("dup"), new RecordingKitSource("dup")),
                new VanceOdeKitProperties()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("both declare id 'dup'");
    }

    @Test
    void build_overTheSizeLimit_is413AndNotAServerError() throws Exception {
        VanceOdeKitProperties tiny = new VanceOdeKitProperties();
        tiny.setMaxBundleBytes(1);
        MockMvc limited = MockMvcBuilders
                .standaloneSetup(new OdeKitController(List.of(source), tiny))
                .build();

        // Waiting changes nothing about a kit that is too big. Read as a 5xx
        // the reader backs off and retries a request that cannot succeed until
        // somebody edits either the kit or the limit.
        limited.perform(post(PATH + "/build")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kit":"acme-crm","tenant":"acme"}"""))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error").value("bundle_too_large"));
    }

    @Test
    void build_whenTheSourceThrows_is500WithAnErrorBodyOfOurOwn() throws Exception {
        // Without a handler this escapes into the host's error handling, and a
        // host that answers 200 with its own error page would have the reader
        // unpack that as a zip.
        MockMvc failing = MockMvcBuilders
                .standaloneSetup(new OdeKitController(
                        List.of(new FailingKitSource("acme-crm")), new VanceOdeKitProperties()))
                .build();

        failing.perform(post(PATH + "/build")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kit":"acme-crm","tenant":"acme"}"""))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("source_failed"));
    }

    @Test
    void construction_withASourceThatCannotDeclareItselfYet_doesNotStopTheHost() {
        // A directory the operations team creates on first deploy must not keep
        // the embedding application's context from starting.
        OdeKitController controller = new OdeKitController(
                List.of(new UndeclarableKitSource(), source), new VanceOdeKitProperties());

        assertThat(controller.capabilities().kits())
                .singleElement()
                .satisfies(k -> assertThat(k.id()).isEqualTo("acme-crm"));
    }

    @Test
    void capabilities_picksUpASourceThatBecameDeclarableAfterStartup() {
        UndeclarableKitSource late = new UndeclarableKitSource();
        OdeKitController controller = new OdeKitController(
                List.of(late, source), new VanceOdeKitProperties());
        assertThat(controller.capabilities().kits()).hasSize(1);

        late.ready = true;

        // Resolved per request precisely so this works — declare() is cheap by
        // contract, and a startup snapshot could only ever go stale.
        assertThat(controller.capabilities().kits()).hasSize(2);
    }

    @Test
    void build_whenASourceCouldNotBeAsked_isNotReportedAsNotServed() throws Exception {
        MockMvc partial = MockMvcBuilders
                .standaloneSetup(new OdeKitController(
                        List.of(new UndeclarableKitSource(), source), new VanceOdeKitProperties()))
                .build();

        // "This application does not serve that" is a 400 the caller acts on,
        // and it cannot be said honestly while a source that might have served
        // it could not be asked.
        partial.perform(post(PATH + "/build")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kit":"other","tenant":"acme"}"""))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("source_failed"));
    }

    @Test
    void build_paramWithoutAValue_isDroppedRatherThanCallingTheBodyInvalidJson()
            throws Exception {
        // `params: {lang: }` in the caller's provisioning file arrives as a
        // JSON null. Map.copyOf used to throw on it inside deserialisation, and
        // the answer blamed the JSON — which was fine.
        mvc.perform(post(PATH + "/build")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kit":"acme-crm","tenant":"acme",
                                 "params":{"lang":null,"modules":"crm"}}"""))
                .andExpect(status().isOk());

        OdeKitBuildRequest request = source.requests.getFirst();
        assertThat(request.params()).containsOnlyKeys("modules");
        assertThat(request.param("lang", "en")).isEqualTo("en");
    }

    private byte[] build() throws Exception {
        return mvc.perform(post(PATH + "/build")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kit":"acme-crm","tenant":"acme"}"""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
    }

    private static Map<String, byte[]> unzip(byte[] archive) throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                files.put(entry.getName(), zip.readAllBytes());
            }
        }
        return files;
    }
}
