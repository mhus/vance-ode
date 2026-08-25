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

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * What happens on a classpath without a YAML writer.
 *
 * <p>This needs a class loader to test, and it earns the machinery: the writer
 * was a static field of {@link CallContext} once, so a runtime missing it failed
 * in the static initialiser and took the whole layer down — the parameter
 * validation and the byte replies included, none of which involve YAML. The
 * error named a Jackson class and a line number in a file nobody had touched,
 * and the second read said only "could not initialise CallContext". Nothing in
 * an ordinary test run can catch that returning, because the writer is always
 * present there.
 *
 * <p>So the writer is hidden deliberately, and the invariant is stated twice:
 * everything except {@code replyYaml} works without it, and {@code replyYaml}
 * refuses with the artifact's name in the message.
 *
 * <p><b>The other half of that guard is not testable here.</b> A classpath can
 * also have the writer and not the {@code snakeyaml-engine} under it, and
 * {@code replyYaml} turns that into the same refusal with the linkage error kept
 * as its cause. A child loader cannot produce it: hiding a package only affects
 * the classes <em>this</em> loader is asked for, and {@code YAMLMapper} comes
 * from the parent, where the engine is present. Verified instead against a real
 * classpath with the engine jar left out — {@code NoClassDefFoundError:
 * org/snakeyaml/engine/v2/events/Event} arrives as the cause of the refusal.
 */
class CallContextWithoutYamlTest {

    @Test
    void withoutTheWriter_validatingAndReplyingWithBytesStillWork() throws Exception {
        Isolated isolated = new Isolated("tools.jackson.dataformat.yaml");

        Object ctx = isolated.context("reports/x.txt", "text/plain");
        isolated.callContext.getMethod("reply", String.class).invoke(ctx, "plain bytes");
        byte[] answer = (byte[]) isolated.callContext.getMethod("answer").invoke(ctx);

        assertThat(new String(answer, StandardCharsets.UTF_8)).isEqualTo("plain bytes");
    }

    @Test
    void withoutTheWriter_replyYamlRefusesAndNamesTheArtifact() throws Exception {
        Isolated isolated = new Isolated("tools.jackson.dataformat.yaml");

        Object ctx = isolated.context("reports/x.yaml", "application/yaml");

        assertThatThrownBy(() -> isolated.callContext
                        .getMethod("replyYaml", Object.class)
                        .invoke(ctx, Map.of("a", 1)))
                .isInstanceOf(InvocationTargetException.class)
                .cause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("jackson-dataformat-yaml")
                .hasMessageContaining("reports/x.yaml");
    }

    /** The endpoint package, reloaded with one library taken away. */
    private static final class Isolated {

        private final ClassLoader loader;
        private final Class<?> endpointSpec;
        private final Class<?> callContext;

        Isolated(String hidden) throws ClassNotFoundException {
            loader = new WithoutYaml(hidden);
            endpointSpec = loader.loadClass(EndpointSpec.class.getName());
            callContext = loader.loadClass(CallContext.class.getName());
        }

        /** A context over a parameterless spec, all inside the isolated loader. */
        Object context(String path, String mime) throws Exception {
            Object spec = endpointSpec
                    .getMethod("of", String.class, String.class, String.class, String.class)
                    .invoke(null, path, mime, "Title", "Description");
            return callContext.getMethod("of", endpointSpec).invoke(null, spec);
        }
    }

    /**
     * Loads the endpoint package itself, refuses the YAML package, and delegates
     * everything else.
     *
     * <p>The endpoint classes have to be loaded <b>here</b> rather than
     * delegated: a class already loaded by the parent carries the parent's view
     * of the classpath, where the writer exists.
     */
    private static final class WithoutYaml extends ClassLoader {

        private static final String RELOADED = "de.mhus.vance.ode.jaglan.endpoint.";

        private final String hidden;

        WithoutYaml(String hidden) {
            super(CallContext.class.getClassLoader());
            this.hidden = hidden;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            if (name.startsWith(hidden)) {
                throw new ClassNotFoundException(name + " is hidden by this test");
            }
            if (!name.startsWith(RELOADED)) {
                return super.loadClass(name, resolve);
            }
            Class<?> already = findLoadedClass(name);
            if (already != null) {
                return already;
            }
            byte[] bytecode = read(name.replace('.', '/') + ".class");
            Class<?> defined = defineClass(name, bytecode, 0, bytecode.length);
            if (resolve) {
                resolveClass(defined);
            }
            return defined;
        }

        private byte[] read(String resource) throws ClassNotFoundException {
            try (InputStream in = getParent().getResourceAsStream(resource)) {
                if (in == null) {
                    throw new ClassNotFoundException(resource);
                }
                return in.readAllBytes();
            } catch (IOException e) {
                throw new ClassNotFoundException(resource, e);
            }
        }
    }
}
