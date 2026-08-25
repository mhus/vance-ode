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
package de.mhus.vance.ode.jaglan.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import de.mhus.vance.ode.inbound.OdeBadRequestException;
import de.mhus.vance.ode.jaglan.FileSource;
import de.mhus.vance.ode.jaglan.OdeFileAccess;
import de.mhus.vance.ode.jaglan.OdeFileCapabilities;
import de.mhus.vance.ode.jaglan.OdeFileEntry;
import de.mhus.vance.ode.jaglan.OdeQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The decorator's own promise: everything the wrapped source did, it still does,
 * and the computed paths sit beside it without the wrapped source knowing.
 */
class EndpointFileSourceTest {

    private RecordingSource inner;
    private EndpointFileSource source;

    @BeforeEach
    void setUp() {
        inner = new RecordingSource();
        source = new EndpointFileSource(inner, "A news archive.",
                List.of(new TrendEndpoint(), new OverviewEndpoint()));
    }

    // ── additive ─────────────────────────────────────────────────────

    @Test
    void wrappedPaths_areUntouched() {
        assertThat(source.stat("article/one.md")).isPresent();
        assertThat(source.list("article")).extracting(OdeFileEntry::path)
                .containsExactly("article/one.md");
        assertThat(read(source.open("article/one.md"))).isEqualTo("the article");
        assertThat(source.search("phrase", 5)).hasSize(1);
    }

    @Test
    void rootListing_keepsTheWrappedEntriesAndAddsTheEndpointFolder() {
        assertThat(source.list("")).extracting(OdeFileEntry::path)
                .containsExactly("article", "reports");
    }

    @Test
    void endpointFolder_listsItsEndpoints() {
        assertThat(source.list("reports")).extracting(OdeFileEntry::path)
                .containsExactly("reports/trends.yaml", "reports/overview.yaml");
    }

    @Test
    void endpointFolder_isNotAskedOfTheWrappedSource() {
        source.list("reports");

        assertThat(inner.listed).doesNotContain("reports");
    }

    @Test
    void endpointFolder_statsAsAFolder() {
        assertThat(source.stat("reports")).get()
                .extracting(OdeFileEntry::folder).isEqualTo(true);
    }

    @Test
    void endpoint_statsWithoutSizeOrEtag() {
        OdeFileEntry entry = source.stat("reports/trends.yaml").orElseThrow();

        assertThat(entry.folder()).isFalse();
        assertThat(entry.mimeType()).isEqualTo("application/yaml");
        assertThat(entry.title()).isEqualTo("Trends");
        assertThat(entry.size()).isZero();
        assertThat(entry.etag()).isNull();
    }

    // ── the api description ──────────────────────────────────────────

    @Test
    void apiDescription_isReadableButNotListed() {
        assertThat(source.stat(EndpointFileSource.API_PATH)).isPresent();
        assertThat(source.list("")).extracting(OdeFileEntry::path)
                .doesNotContain(EndpointFileSource.API_PATH);
    }

    @Test
    void apiDescription_namesEveryEndpointAndItsParameters() {
        String yaml = read(source.open(EndpointFileSource.API_PATH));

        assertThat(yaml)
                .contains("about: A news archive.")
                .contains("path: reports/trends.yaml")
                .contains("path: reports/overview.yaml")
                .contains("name: dimension")
                .contains("type: select")
                .contains("defaultValue: topic")
                .contains("- topic")
                .contains("usage:");
    }

    @Test
    void apiDescription_doesNotDescribeItself() {
        ApiDescription described = source.describe();

        assertThat(described.endpoints()).extracting(ApiDescription.Endpoint::path)
                .doesNotContain(EndpointFileSource.API_PATH);
    }

    @Test
    void apiDescription_refusesAQueryLikeAnyOtherEndpoint() {
        assertThatThrownBy(() -> source.open(EndpointFileSource.API_PATH,
                        new OdeQuery(Map.of("verbose", List.of("true")))))
                .isInstanceOf(OdeBadRequestException.class);
    }

    // ── another format of the same answer ────────────────────────────

    @Test
    void aRendering_isListedAndReadableLikeAnyOtherEndpoint() {
        EndpointFileSource withMarkdown = withMarkdown();

        assertThat(withMarkdown.list("reports")).extracting(OdeFileEntry::path)
                .contains("reports/trends.md");
        assertThat(read(withMarkdown.open("reports/trends.md"))).isEqualTo("# topic");
    }

    @Test
    void aRendering_hasNoEntryOfItsOwnButAppearsAsAFormat() {
        ApiDescription described = withMarkdown().describe();

        assertThat(described.endpoints()).extracting(ApiDescription.Endpoint::path)
                .containsExactly("reports/trends.yaml", "reports/overview.yaml");
        assertThat(described.endpoints().get(0).alsoAt())
                .extracting(ApiDescription.Format::path)
                .containsExactly("reports/trends.md");
        assertThat(described.endpoints().get(0).alsoAt().get(0).mime())
                .isEqualTo("text/markdown");
    }

    @Test
    void aRendering_takesTheSameParametersAsWhatItRenders() {
        InputStream answer = withMarkdown().open("reports/trends.md",
                new OdeQuery(Map.of("dimension", List.of("source"))));

        assertThat(read(answer)).isEqualTo("# source");
    }

    @Test
    void aRenderingOfNothing_isRefusedAtConstruction() {
        assertThatThrownBy(() -> new EndpointFileSource(inner,
                        List.of(new MarkdownTrendEndpoint())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("which no endpoint serves");
    }

    @Test
    void aRenderingWithOtherParameters_isRefusedAtConstruction() {
        assertThatThrownBy(() -> new EndpointFileSource(inner,
                        List.of(new TrendEndpoint(), new DivergingRenderingEndpoint())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must accept exactly its parameters");
    }

    @Test
    void aRenderingOfARendering_isRefusedAtConstruction() {
        assertThatThrownBy(() -> new EndpointFileSource(inner, List.of(new TrendEndpoint(),
                        new MarkdownTrendEndpoint(), new ChainedRenderingEndpoint())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("formats are one level");
    }

    private EndpointFileSource withMarkdown() {
        return new EndpointFileSource(inner, "A news archive.", List.of(
                new TrendEndpoint(), new MarkdownTrendEndpoint(), new OverviewEndpoint()));
    }

    // ── reading a computed path ──────────────────────────────────────

    @Test
    void endpointWithoutAQuery_answersWithItsDefaults() {
        assertThat(read(source.open("reports/trends.yaml"))).isEqualTo("dimension: topic");
    }

    @Test
    void endpointWithAQuery_answersWithTheGivenValues() {
        InputStream answer = source.open("reports/trends.yaml",
                new OdeQuery(Map.of("dimension", List.of("source"))));

        assertThat(read(answer)).isEqualTo("dimension: source");
    }

    @Test
    void queryAgainstAWrappedPath_isLeftToTheWrappedSource() {
        assertThatThrownBy(() -> source.open("article/one.md",
                        new OdeQuery(Map.of("from", List.of("2026-01")))))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void endpointThatAnswersNothing_failsRatherThanServingAnEmptyFile() {
        EndpointFileSource silent = new EndpointFileSource(inner, List.of(new SilentEndpoint()));

        assertThatThrownBy(() -> silent.open("reports/silent.yaml"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("without answering");
    }

    // ── capabilities ─────────────────────────────────────────────────

    @Test
    void supportsQuery_isRaisedWhenAnEndpointTakesParameters() {
        assertThat(inner.capabilities().supportsQuery()).isFalse();
        assertThat(source.capabilities().supportsQuery()).isTrue();
    }

    @Test
    void supportsQuery_staysFalseWhenNoEndpointTakesParameters() {
        EndpointFileSource plain = new EndpointFileSource(inner, List.of(new OverviewEndpoint()));

        assertThat(plain.capabilities().supportsQuery()).isFalse();
    }

    @Test
    void everyOtherCapability_isTheWrappedSourcesOwn() {
        OdeFileCapabilities caps = source.capabilities();

        assertThat(caps.displayName()).isEqualTo("Inner");
        assertThat(caps.canSearch()).isTrue();
        assertThat(caps.itemCount()).isEqualTo(7L);
        assertThat(caps.metadataTtl()).isEqualTo(Duration.ofMinutes(2));
    }

    // ── writing ──────────────────────────────────────────────────────

    @Test
    void writingAComputedPath_isRefused() {
        assertThatThrownBy(() -> source.write("reports/trends.yaml", stream("x")))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("computed");
    }

    @Test
    void deletingAComputedPath_isRefused() {
        assertThatThrownBy(() -> source.delete(EndpointFileSource.API_PATH))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("computed");
    }

    @Test
    void writingAWrappedPath_passesThrough() {
        assertThat(source.write("article/two.md", stream("x")).path()).isEqualTo("article/two.md");
    }

    // ── construction ─────────────────────────────────────────────────

    @Test
    void twoEndpointsAtOnePath_areRefused() {
        assertThatThrownBy(() -> new EndpointFileSource(
                        inner, List.of(new TrendEndpoint(), new TrendEndpoint())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("two endpoints claim");
    }

    @Test
    void anEndpointAtTheApiPath_isRefused() {
        assertThatThrownBy(() -> new EndpointFileSource(inner, List.of(new ImpostorEndpoint())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("describes itself");
    }

    @Test
    void noEndpointsAtAll_changesNothing() {
        EndpointFileSource bare = new EndpointFileSource(inner, List.of());

        assertThat(bare.list("")).extracting(OdeFileEntry::path).containsExactly("article");
        assertThat(bare.capabilities()).isEqualTo(inner.capabilities());
        assertThat(bare.stat(EndpointFileSource.API_PATH)).isPresent();
    }

    // ── fixtures ─────────────────────────────────────────────────────

    private static String read(InputStream stream) {
        try (InputStream open = stream) {
            return new String(open.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static InputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    /** A holding, with one file in one folder. */
    private static final class RecordingSource implements FileSource {

        private final List<String> listed = new ArrayList<>();

        @Override
        public OdeFileCapabilities capabilities() {
            return new OdeFileCapabilities(OdeFileAccess.READ_WRITE, true, 7L,
                    Duration.ofMinutes(2), null, false, "Inner");
        }

        @Override
        public Optional<OdeFileEntry> stat(String path) {
            if ("article".equals(path)) {
                return Optional.of(OdeFileEntry.folder("article"));
            }
            return "article/one.md".equals(path)
                    ? Optional.of(OdeFileEntry.file("article/one.md", 11, "text/markdown", "\"e\""))
                    : Optional.empty();
        }

        @Override
        public List<OdeFileEntry> list(String path) {
            listed.add(path);
            if (path.isEmpty()) {
                return List.of(OdeFileEntry.folder("article"));
            }
            return "article".equals(path)
                    ? List.of(OdeFileEntry.file("article/one.md", 11, "text/markdown", "\"e\""))
                    : List.of();
        }

        @Override
        public InputStream open(String path) {
            return stream("the article");
        }

        @Override
        public OdeFileEntry write(String path, InputStream content) {
            return OdeFileEntry.file(path, 1, "text/markdown", null);
        }

        @Override
        public List<OdeFileEntry> search(String query, int limit) {
            return List.of(OdeFileEntry.file("article/one.md", 11, "text/markdown", "\"e\""));
        }
    }

    private static final class TrendEndpoint implements MountEndpoint {

        @Override
        public EndpointSpec spec() {
            return EndpointSpec.of("reports/trends.yaml", "application/yaml", "Trends",
                    "What was counted in a window.",
                    EndpointParam.select("dimension", List.of("topic", "source"), "topic",
                            "what to group by"));
        }

        @Override
        public void handle(CallContext ctx) {
            ctx.reply("dimension: " + ctx.text("dimension"));
        }
    }

    /** The same report as Markdown — one entry in the description, two paths. */
    private static final class MarkdownTrendEndpoint implements MountEndpoint {

        @Override
        public EndpointSpec spec() {
            return EndpointSpec.of("reports/trends.md", "text/markdown", "Trends",
                    "The same report, formatted for reading.",
                    EndpointParam.select("dimension", List.of("topic", "source"), "topic",
                            "what to group by"))
                    .asRenderingOf("reports/trends.yaml");
        }

        @Override
        public void handle(CallContext ctx) {
            ctx.reply("# " + ctx.text("dimension"));
        }
    }

    /** A rendering that accepts something else — a documented lie, refused. */
    private static final class DivergingRenderingEndpoint implements MountEndpoint {

        @Override
        public EndpointSpec spec() {
            return EndpointSpec.of("reports/trends.md", "text/markdown", "Trends",
                    "Claims to be the same report and takes another parameter.",
                    EndpointParam.optional("limit", ParamType.INTEGER, "20", "how many"))
                    .asRenderingOf("reports/trends.yaml");
        }

        @Override
        public void handle(CallContext ctx) {
            ctx.reply("# nope");
        }
    }

    /** A rendering of a rendering — formats are one level. */
    private static final class ChainedRenderingEndpoint implements MountEndpoint {

        @Override
        public EndpointSpec spec() {
            return EndpointSpec.of("reports/trends.txt", "text/plain", "Trends",
                    "A rendering of the Markdown rendering.",
                    EndpointParam.select("dimension", List.of("topic", "source"), "topic",
                            "what to group by"))
                    .asRenderingOf("reports/trends.md");
        }

        @Override
        public void handle(CallContext ctx) {
            ctx.reply("nope");
        }
    }

    private static final class OverviewEndpoint implements MountEndpoint {

        @Override
        public EndpointSpec spec() {
            return EndpointSpec.of("reports/overview.yaml", "application/yaml", "Overview",
                    "How the collector is doing.");
        }

        @Override
        public void handle(CallContext ctx) {
            ctx.reply("sources: 3");
        }
    }

    private static final class SilentEndpoint implements MountEndpoint {

        @Override
        public EndpointSpec spec() {
            return EndpointSpec.of("reports/silent.yaml", "application/yaml", "Silent",
                    "Answers nothing, which is a bug.");
        }

        @Override
        public void handle(CallContext ctx) {
            // deliberately nothing
        }
    }

    private static final class ImpostorEndpoint implements MountEndpoint {

        @Override
        public EndpointSpec spec() {
            return EndpointSpec.of(EndpointFileSource.API_PATH, "application/yaml", "Mine",
                    "Claims the description's own path.");
        }

        @Override
        public void handle(CallContext ctx) {
            ctx.reply("no");
        }
    }
}
