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
package de.mhus.vance.ode.jaglan;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;

/**
 * The one interface a foreign application implements to make its files
 * readable inside Vancetope. Publish it as a bean and this module serves the
 * REST contract.
 *
 * <p>On the other side your files appear under a project path
 * ({@code _ext/<mount>/…}) and can be opened, linked and embedded with the
 * ordinary document tools. Nothing is copied: the reader keeps a metadata row
 * per file and streams your bytes on every read.
 *
 * <h2>Four assurances the contract rests on</h2>
 * <ol>
 *   <li><b>Paths are stable.</b> This is the whole difference from a search
 *       result: a reader stores {@code books/dune.pdf} in a link, a document
 *       reference, a binder entry — and expects it to still mean the same file
 *       tomorrow. If your ids churn, you are a search source
 *       ({@code vance-ode-zarniwoop}), not a mount.
 *   <li><b>{@link #stat} distinguishes "gone" from "broken".</b> Return empty
 *       for a file you do not have; <b>throw</b> when you cannot answer. The
 *       reader deletes its metadata row on the first and keeps it on the
 *       second, and getting this backwards means a temporary outage tells
 *       somebody their document does not exist.
 *   <li><b>{@link #list} is authoritative for its own folder.</b> What you
 *       leave out, the reader removes. Do not return a partial page to save
 *       time — there is no paging here, and a truncated listing looks like
 *       deletion.
 *   <li><b>{@link #open} streams.</b> You are handed no size limit to respect
 *       beyond the one you declared; do not materialise a large file in memory
 *       to answer, and do not close the stream you return — the endpoint does.
 * </ol>
 *
 * <p>Implementations must be safe to call from several threads at once.
 */
public interface FileSource {

    /**
     * What this source allows and how long its answers may be cached. Called
     * behind a cache, so it may be computed rather than constant.
     */
    OdeFileCapabilities capabilities();

    /**
     * One entry, or {@link Optional#empty()} when you do not have it.
     *
     * <p>Empty is an <b>answer</b>, not a failure — the reader treats it as
     * authoritative and forgets the file. Throw if you cannot answer.
     */
    Optional<OdeFileEntry> stat(String path);

    /**
     * Direct children of a folder — one level, not recursive.
     *
     * @param path the folder; empty string for your root
     */
    List<OdeFileEntry> list(String path);

    /**
     * Open a file for reading. The caller closes the stream.
     *
     * <p>Throw for a path you do not have; the endpoint turns that into a 404
     * after checking {@link #stat}, so the common case never reaches here.
     */
    InputStream open(String path);

    /**
     * Write a file. Default refuses.
     *
     * <p>Only called when you declared {@link OdeFileAccess#READ_WRITE}, so
     * the default is reached only if the declaration and the implementation
     * disagree.
     *
     * @return the entry as it now stands — size and etag included, since the
     *         reader stores them and would otherwise have to stat again
     */
    default OdeFileEntry write(String path, InputStream content) {
        throw new UnsupportedOperationException("this file source is read-only");
    }

    /**
     * Delete a file. Default refuses.
     *
     * <p>There is no trash on the reader's side for mounted files — its trash
     * folder lives outside the mount namespace, so moving a file there would
     * break the address it is known by. A delete that reaches you is meant.
     */
    default void delete(String path) {
        throw new UnsupportedOperationException("this file source is read-only");
    }

    /**
     * Search your own catalogue. Default returns nothing.
     *
     * <p>Only called when {@link OdeFileCapabilities#canSearch()} is true, so
     * an empty list here never has to be read as "found nothing". Worth
     * implementing for anything with an index: the alternative is the reader
     * walking your tree, which is slower for it and heavier for you.
     */
    default List<OdeFileEntry> search(String query, int limit) {
        return List.of();
    }
}
