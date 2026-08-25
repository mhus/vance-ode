# Vance Ode

A Spring Boot library that connects foreign software to a
[Vancetope](https://github.com/mhus/vance) brain — in both directions.

Ode is what an application embeds when it wants to *use* a brain (fire an
event, get a translation back) or to *be used by* one (answer a search,
supply a feed). It carries the connection configuration, the error model
and the transport so the application does not re-derive them.

> **Status:** early. Five subsystems implemented (`ursa`, `centauri`,
> `zarniwoop`, `kit`, `jaglan`).
> **Licence:** Apache-2.0.

## Modules

One module per Vancetope subsystem the application takes part in.

| Module | Subsystem | Direction | Status |
|---|---|---|---|
| `vance-ode-core` | — | — | shared config, error model, HTTP transport, inbound guard |
| `vance-ode-ursa` | events / triggers | outbound | **implemented** |
| `vance-ode-centauri` | feed streams | inbound | **implemented** |
| `vance-ode-zarniwoop` | research / search | inbound | **implemented** |
| `vance-ode-kit` | kit provisioning | inbound | **implemented** |
| `vance-ode-jaglan` | mounted files | inbound | **implemented** |

### Why by subsystem and not by direction

The obvious alternative is to split client from server, and it was the
first thing tried. It does not survive contact with the actual
subsystems: four of the five are **inbound** contracts, where the
application answers a brain's request. Each of those is one thing — DTOs,
endpoints, semantics — and splitting it across a client half and a server
half would separate exactly what belongs together.

The reason to have modules at all is dependency hygiene, and a
subsystem split satisfies it just as well: `vance-ode-ursa` is HTTP only
and pulls no Spring Web, while the inbound modules will declare it
themselves. An application that only fires events does not inherit a
servlet stack for the privilege.

**When to add a module:** when a subsystem gets its first real contract.
Not before — a module with a pom and no classes is structure without
content, and which controller belongs in `centauri` is not knowable until
a brain asks for one.

**No controller in `vance-ode-core`.** That is the rule that keeps the
outbound-only case lean, and it is about the servlet container a controller
drags in, not about the `org.springframework.web` types as such. Core does
carry `de.mhus.vance.ode.inbound` — the shared-secret guard and the error
body that every inbound module needs — with its two Spring Web dependencies
`provided`, and provided scope is not transitive: an application that only
fires events downloads nothing extra. The alternative was one copy of the
guard per inbound module, and a duplicated authentication check drifts.

## Using it

Released to [Maven Central](https://central.sonatype.com/namespace/de.mhus.vance.ode)
under the group `de.mhus.vance.ode` — no repository declaration needed. Take only
the modules you take part in:

```xml
<dependency>
    <groupId>de.mhus.vance.ode</groupId>
    <artifactId>vance-ode-ursa</artifactId>
    <version>0.1.0</version>
</dependency>
```

```yaml
vance:
  ode:
    base-url: https://brain.example.com
    tenant: acme
    project: giant-slingshot
    events:
      translate-article:
        token: ${VANCE_TRANSLATE_TOKEN}
        timeout: PT120S      # a model call is slow by HTTP standards
```

```java
@RequiredArgsConstructor
class TranslationService {

    private final UrsaEventClient events;

    String toGerman(String text) {
        return events.requireText("translate-article",
                Map.of("text", text, "targetLang", "de"));
    }
}
```

Nothing is auto-wired until `vance.ode.base-url` is set. A dependency
that starts talking to a server merely by being on the classpath would be
a bad neighbour in software it was embedded in.

### Events must be declared

`vance.ode.events.<name>` is not just where the token lives — an event
that is not declared cannot be fired. That is deliberate: it keeps the
set of things this application can set off in a brain readable from its
own configuration, rather than scattered across whatever strings its code
passes.

### What comes back

A Vancetope event answers differently depending on how it is configured,
and `EventResult` reflects that rather than hiding it:

| Event | `runId` | `output` |
|---|---|---|
| synchronous script | – | the script's return value |
| recipe / workflow spawn | the run id | – |
| `async: true` | – | – |

`text()` unwraps the common case: a script returning a scalar arrives
under `output.value`, which is the brain's mapping convention.
`requireText` turns "no output" into an exception that names the likely
cause — an event declared as a translator that turns out to be a spawn
should fail loudly, not return nothing.

Field names are Ode's, not the wire's. The brain still calls these
`workflowName` and `workflowRunId` for historical reasons even when they
carry a script target, and its own specification flags that as
misleading; repeating it would be a poor thing for an SDK to do.

## Serving a feed

The inbound direction. Vancetope's feed reader — Centauri — asks this
application for time-ordered entries and merges them with other sources into
one endless scroll. Implement one interface and the REST contract is served:

```xml
<dependency>
    <groupId>de.mhus.vance.ode</groupId>
    <artifactId>vance-ode-centauri</artifactId>
    <version>0.1.0</version>
</dependency>
```

```java
@Component
class NewsFeedSource implements FeedSource {

    @Override
    public OdeCapabilities capabilities() {
        return OdeCapabilities.readOnly(100);
    }

    @Override
    public List<OdeSelector> selectors() {
        return List.of(OdeSelector.category("world", "World"),
                       OdeSelector.category("tech", "Technology"));
    }

    @Override
    public OdeItemPage items(OdeItemQuery query) {
        var rows = repository.byCategory(query.selector(), query.cursor(), query.limit());
        return new OdeItemPage(rows.map(this::toItem), rows.lastId(), rows.hasMore());
    }

    // withCursor only if your cursor is not the plain item id — see
    // "Two cursors" below, where the silent failure is spelled out.
    private OdeItem toItem(Row row) {
        return OdeItem.of(row.id(), row.publishedAt(), row.title(), row.url())
                .withCursor(row.publishedAt() + "|" + row.id());
    }
}
```

```yaml
vance:
  ode:
    centauri:
      path: /ode/feed        # default; change it and tell the reader
      api-key: ${FEED_KEY}   # empty means no check — see below
```

No `vance.ode.base-url` is involved: answering a request needs no brain.
The bean is the switch — without a `FeedSource` nothing is mapped, because an
unwanted endpoint is worse than a dormant client. It is reachable.

### The contract

| Endpoint | Purpose |
|---|---|
| `GET {path}/capabilities` | what this source can do; cached, reader-independent |
| `GET {path}/selectors` | the finite taxonomy, for sources that have one |
| `GET {path}/items` | one page of one stream |
| `GET {path}/item/{id}` | full text, for sources whose list is a teaser |
| `GET {path}/facets` | one level of a facet's value tree, for trees too large to travel inline |
| `POST {path}/signal` | the back channel (see below) |

Timestamps are ISO-8601 instants, the capabilities TTL an ISO-8601 duration —
self-describing matters more between two systems than brevity.

### Two cursors, and you probably need both

`OdeItemPage.nextCursor` resumes after the batch you returned. `OdeItem.cursor`
resumes after **one entry** — and that is the one the reader reaches for most,
because it merges your stream with others and therefore usually cuts your batch
in the middle. A page-level token cannot describe that cut.

Leave `OdeItem.cursor` null only if your cursor really is the bare item id; the
reader falls back to that. If you page by `(publishedAt, id)` — the honest
scheme, because timestamps are not unique — the fallback is wrong, and wrong
**silently**: you receive a bare id, cannot parse it, start from the top, and the
reader's scroll repeats a page instead of advancing. Nothing errors.

One more shape to avoid: an **empty page with `hasMore: true` and no
`nextCursor`**. Nothing about it is representable as progress, so the reader
retires the stream for that scroll and logs why, rather than asking the identical
question forever.

### Three assurances

1. **Pages come back chronologically.** Personalise *which* entries appear if
   you like; never their order. The reader merges your page with other sources
   on the timestamp, so a per-reader ranking does not look broken — it quietly
   produces a wrong sequence. A page in the wrong order is logged here, where
   the person who can fix it will see it.
2. **`items` answers without a reader pseudonym.** Scheduled digests have no
   person behind them. A source that needs the pseudonym to respond breaks them.
3. **`capabilities` and `selectors` never receive one.** They describe the
   source, so they are cached across all readers.

### The reader pseudonym

`X-Vance-Reader` is opaque and salted per source, which means it is meaningless
anywhere but here — two sources cannot join profiles over the same person. Use
it to personalise selection or keep read marks. Never to authorise, and never
as an identifier of a human: you are not being told who is reading, on purpose.

It rides in a header rather than the query string because a value like this
should not end up in access logs and intermediary cache keys.

### The back channel

Deliberately small: `REPORT` (wrong category, wrong language, broken link,
duplicate, spam) and `REQUEST` (produce and keep a translation or a full text).
The admission rule is that **a signal describes the item, not the reader** —
which is why there is no "like" and why you are never asked to store somebody's
preferences.

Everything else reaches you as a deep link into your own UI instead: put a URL
on `OdeItem.controlUrl` and the reader offers it as a way out of Centauri and
into your interface. That keeps this vocabulary from growing while leaving you
free to build whatever your own UI can express.

Signals are fire-and-forget. Answer that you received one, not what you will do
with it — Vancetope accordingly tells the reader "reported" and nothing more.

### The shared secret

`api-key` empty means **no check**, which is a decision rather than an
oversight: an application embedding this module may already guard the path, and
a library insisting on a second scheme it invented would be fighting its host.
Set it when the endpoint would otherwise be reachable by anyone; it is then
expected as `Authorization: Bearer <key>` and compared in constant time.

For more than one reader — several tokens, rotation, revocation, or knowing
*which* installation is calling — see [Who may call](#who-may-call).

## Zarniwoop — being searched

The inbound direction again, and a different act. Centauri asks for entries in
time order and a reader scrolls them; Zarniwoop asks a **question** and expects
answers. An application may implement both interfaces and many will, but neither
implies the other — a search index has no chronology to page through, and a feed
has no query to answer.

The point is that **the research options come from this service**: what can be
searched here is declared here, not compiled into Vancetope.

```xml
<dependency>
    <groupId>de.mhus.vance.ode</groupId>
    <artifactId>vance-ode-zarniwoop</artifactId>
    <version>0.1.0</version>
</dependency>
```

```java
@Component
class NewsSearchSource implements SearchSource {

    @Override
    public OdeSearchCapabilities capabilities() {
        return OdeSearchCapabilities.of(OdeSearchModality.NEWS, 25);
    }

    @Override
    public OdeSearchResponse search(OdeSearchQuery query) {
        var rows = index.query(query.query(), query.maxResults());
        if (rows.isEmpty()) {
            return OdeSearchResponse.empty("no article matches");
        }
        return OdeSearchResponse.of(rows.stream().map(this::toHit).toList());
    }
}
```

```yaml
vance:
  ode:
    zarniwoop:
      path: /ode/search        # default; change it and tell the reader
      api-key: ${SEARCH_KEY}   # empty means no check — same rule as centauri
      max-results: 50          # what one request may cost, whatever a source declares
```

### The contract

| Endpoint | Purpose |
|---|---|
| `GET {path}/capabilities` | what can be searched here; cached, caller-independent |
| `POST {path}/search` | the search itself |
| `GET {path}/facets` | one level of a facet's value tree, for trees too large to travel inline |
| `GET {path}/content/{id}` | **optional** — the body of a hit, for expensive full texts |

`search` is a POST although it changes nothing: `expertParams` is a structured
map, and squeezing it into a query string would invent an encoding both sides
would then have to agree on.

### Three assurances

1. **`capabilities` is caller-independent and cheap.** It is cached and reused
   for every caller, so it must not reach out over a network per request. A
   source whose abilities depend on who is asking says so by answering fewer
   queries, not by varying this.
2. **`search` answers in seconds, not minutes.** Vancetope runs it synchronously
   inside a tool call, with a person waiting on the turn. Return what is ready
   and say so in `note`; a partial answer beats a late one.
3. **An empty result is not a failure.** `hits: []` with a `note` is the right
   answer to "nothing found". A 5xx is not: the caller reads it as this source
   being broken and stops asking for minutes, so "no news today" would also
   mean "no news tomorrow". Throw when the search could not be *run*.

### What the contract does not carry

- **No reader identity.** Not a header, not a field — unlike Centauri, which has
  a pseudonym. A search query is not a reading history, and personalised search
  is a decision with its own justification. The field is absent so that nobody
  can start depending on it before that decision is made.
- **No prompt hint.** Vancetope can show a provider's own text to the model, and
  a source explaining itself is appealing — but that is foreign text in a system
  prompt, which is a separate question. Until it is answered there is nothing to
  fill.
- **No cursor.** Search has `maxResults`. A caller that wants a continuous
  stream wants Centauri.

### Closed vocabularies

`OdeSearchModality` and `OdeSearchDomain` are enums, mirrored from Vancetope's
own. They are closed because the LLM tool schemas enumerate their values, and a
free-text field here would break that guarantee at the far end of the wire.
Mirroring them as enums rather than accepting strings means an implementer finds
out at compile time, not from a deserialisation error.

Map onto the nearest existing value: a news index is `NEWS`, a document archive
is `INTERNAL_DOC`. A genuinely new modality (`LEGAL`, `PATENT`) is a change to
both sides of the contract, not something one source invents.

### Optional fields must not be primitives

`maxResults` in the request body is a boxed `Integer`, deliberately. Jackson 3
fails deserialisation when the JSON field for a primitive is missing, and it does
so **before any handler runs** — the caller gets a bodiless 400 for a field the
contract calls optional, with nothing to read. Anything optional added to an
inbound body later follows the same rule.

## Kit — being installed

The third inbound contract, and the one that is about *this application* rather
than about its content. A **kit** is the bundle of documents, tool definitions,
manuals and settings that teaches Vancetope how to work with your software.
Written by hand it drifts from the API it describes; served from here it is
whatever the running version says it is.

```xml
<dependency>
    <groupId>de.mhus.vance.ode</groupId>
    <artifactId>vance-ode-kit</artifactId>
    <version>0.2.0</version>
</dependency>
```

The short way — the kit is a directory of files in your jar:

```java
@Bean
KitSource crmKit() {
    return StaticKitSource.fromClasspath("acme-crm", "kits/acme-crm");
}
```

The long way, when the *file list* differs per project — a licensed module, one
tool per configured object type. Neither end changes; a reader cannot tell which
of the two it is talking to:

```java
@Component
class CrmKitSource implements KitSource {

    @Override
    public OdeKitDeclaration declare() {
        return new OdeKitDeclaration("acme-crm", "1.4.0", index.revision(), "CRM tools");
    }

    @Override
    public OdeKitBundle build(OdeKitBuildRequest request) {
        var files = baseFiles();
        if (billing.enabledFor(request.tenant())) {
            files.put("tools/invoice.yaml", invoiceTool());
        }
        return new OdeKitBundle(files);
    }
}
```

```yaml
vance:
  ode:
    kit:
      path: /kit               # sub-path, not the whole address — see below
      api-key: ${KIT_KEY}      # empty means no check; a kit carries tool definitions
      max-bundle-bytes: 33554432
```

### The contract

| Endpoint | Purpose |
|---|---|
| `GET {path}/capabilities` | which kits are on offer, with a revision each; cheap, builds nothing |
| `POST {path}/build` | assemble one kit, answered as a zip |

`build` is a POST although it changes nothing here: the request carries a
structured body, and a url is where caches and access logs keep things — the
wrong place for the name of a tenant.

### Placeholders are filled by the reader

Put `{{ accessUrl }}` in a file, list that file under `render:` in your
`kit.yaml`, and Vancetope substitutes it on arrival. Available:
`accessUrl`, `tenant`, `project`, `instance`.

Do **not** substitute them yourself. The request tells you the address the caller
reached you at — useful, because behind a reverse proxy you do not reliably know
it — but the caller fills in the value *it sent*. A host that answered with a
different address could point the kit somewhere else, which is why the direction
is this way round.

### Three assurances

1. **`declare()` is cheap and builds nothing.** A reader checks on a schedule
   whether anything changed. If answering that costs what an install costs, the
   check has to be made rare, and then changes arrive late.
2. **`build` answers in seconds.** It runs inside an install someone is waiting
   on.
3. **The revision moves exactly when the bytes move.** Standing still while the
   content changes means the change is never picked up; moving while the content
   stands still means every check refetches. A content hash is the safe answer —
   `StaticKitSource` computes one; a build id works if it obeys the same rule.

And one about failure: **serving no kit is not an exception.** Declare nothing,
or publish no bean. Throwing marks this application as broken and makes the
reader back off — right for a real fault, wrong for "nothing configured here".

### What the request does not carry

No person. It names the installation, the tenant and the project — where the kit
is going, so a failure can be found in your log — and not who triggered it.
`instance` is a label the caller chose for itself: fine for a log, **not** an
authorisation input. Authorise on the credential.

No signature either. You write the kit and you deliver it, so a signature would
prove nothing the transport and the credential do not already say; Vancetope
treats these sources as unsigned by default.

### Why `path` is a sub-path

Unlike the other inbound modules, the reader configures your **application** base
url and sends it back as `accessUrl` so a kit can put it where it needs one. If
the reader's url already contained `/kit`, `accessUrl` would be the address of
this endpoint rather than of the application — and a template cannot strip a
suffix. So the reader knows the root, this module owns the sub-path.

## Jaglan — being mounted

The fourth inbound contract, and the third question a brain can ask about
foreign content. Zarniwoop asks "what do you have on this topic" and Centauri
"what is new"; Jaglan asks **"give me *these* bytes at *this* path"**.

The difference that decides which one you are is **addressability**. A path is
stored in a link, a document reference, a binder entry, and is expected to mean
the same file tomorrow. If your ids churn, you are a search source, not a mount.

Your files appear inside Vancetope under a project path (`_ext/<mount>/…`) and
are opened, linked and embedded with the ordinary document tools. Nothing is
copied: it keeps a metadata row per file and streams your bytes on every read.

```xml
<dependency>
    <groupId>de.mhus.vance.ode</groupId>
    <artifactId>vance-ode-jaglan</artifactId>
    <version>0.2.0</version>
</dependency>
```

```java
@Component
class LibraryFileSource implements FileSource {

    @Override
    public OdeFileCapabilities capabilities() {
        return OdeFileCapabilities.readOnly();
    }

    @Override
    public Optional<OdeFileEntry> stat(String path) {
        // Optional.empty() is an ANSWER — see the assurances below.
        return catalogue.find(path)
                .map(row -> OdeFileEntry.file(row.path(), row.size(), row.mime(), row.etag()));
    }

    @Override
    public List<OdeFileEntry> list(String path) {
        return catalogue.childrenOf(path).stream().map(this::toEntry).toList();
    }

    @Override
    public InputStream open(String path) {
        return storage.read(path);   // the endpoint closes it
    }
}
```

```yaml
vance:
  ode:
    jaglan:
      path: /ode/files          # default; change it and tell the reader
      api-key: ${FILES_KEY}     # empty means no check — read the warning below
      max-search-limit: 200     # what one search may cost, whatever a source declares
```

The bean is the switch, as everywhere here: without a `FileSource` nothing is
mapped.

### The contract

| Endpoint | Purpose |
|---|---|
| `GET {path}/capabilities` | what this source allows, and how long its answers may be cached |
| `GET {path}/stat?path=…` | metadata for one path; 404 when you do not have it |
| `GET {path}/list?path=…` | direct children of a folder, one level; omit `path` for your root |
| `GET {path}/content?path=…` | the bytes, streamed — no JSON envelope, no base64 |
| `PUT {path}/content?path=…` | replace the bytes; 405 for a read-only source |
| `DELETE {path}/content?path=…` | delete at the source; 405 for a read-only source |
| `GET {path}/search?q=…` | your own catalogue, when you declared `canSearch` |

### Four assurances

1. **Paths are stable.** The whole difference from a search result, and the
   reason this contract exists.
2. **`stat` distinguishes "gone" from "broken".** `Optional.empty()` for a file
   you do not have; **throw** when you cannot answer. The reader deletes its
   metadata row on the first and keeps it on the second — get it backwards and a
   two-minute outage tells somebody their document does not exist.
3. **`list` is authoritative for its own folder.** What you leave out, the
   reader removes. There is no paging here, and a truncated listing looks like
   deletion.
4. **`open` streams and does not close.** You declared your own size ceiling in
   `maxBytes`; do not materialise a large file to answer, and leave the stream
   open — the endpoint closes it.

### What the status codes mean

`404` versus `5xx` is the load-bearing line, the same one as everywhere else in
Ode: 404 is an **answer** the reader acts on, 5xx is a failure it backs off
from. An unknown error counts as transient, which is the safer of the two.

`405` is reserved for **read-only** and nothing else. It is a property of the
source rather than of who is asking — a 403 would send a reader looking for a
credential problem — and the reader treats it as stable and stops asking. Which
is why an `UnsupportedOperationException` out of `list` or `search` is a 500 and
not a 405: that is what every immutable collection in the JDK throws, so it is
an ordinary bug inside a source, not a decision it made.

`413` means the file is over the ceiling *you* declared in `maxBytes`. It is
checked here so a source does not have to repeat its own limit.

### The path you are handed is already defended

Leading and trailing slashes are stripped, and `.`/`..` segments, `\`, empty
segments and drive letters (`C:`) are refused with a 400 before you see them.
So `rootDir.resolve(path)` is a safe implementation of `open` — which is the
implementation the contract invites, and the reason the check is strict about
separators this end does not itself use.

### Computed views — a path plus a query

One of your paths, given a query string, may be a **computed view of itself**:

```
_ext/library/reports/loans.yaml               → the defaults
_ext/library/reports/loans.yaml?at=2026-08    → the same path, another answer
```

Declare `supportsQuery` and override `open(path, OdeQuery)`. The reader sends no
query unless you declared it, and the default implementation **throws** rather
than serving the plain file — which is the rule the whole feature rests on:

**Refusing beats ignoring.** A file served without regard for its parameters is
not an error anybody can see. It is a report for the wrong window, correct in
every visible respect except the one that was asked about. If you cannot honour a
parameter, throw.

Three consequences worth knowing before you build on it:

- **A parameterised read is never cached** — that is the reader's rule, not a
  hint. Something cacheable takes no parameters and lives at an ordinary path.
- **A parameterised read appears in no listing.** `list` enumerates what *exists*;
  a view is something you can *ask for*, and the parameter space is yours and not
  finite. There is nothing to enumerate, so the declaration is the only way anyone
  finds out these paths take parameters at all.
- **A view keeps the mime type its path declared in `stat`.** The reader renders
  from its own metadata row, so a differing type would not survive the trip. Two
  formats means two paths.

### The endpoint layer — declared parameters, validated once

`FileSource` already routes a path and carries parameters. What it has no place
for is the **declaration**: which parameters exist, of what type, which are
required. Without that, every source hand-parses its own query — and dropping a
parameter looks like success at every level it passes through.

`de.mhus.vance.ode.jaglan.endpoint` is an **optional, additive** layer over the
contract above. Nothing in it is required, nothing in the base package knows it
exists, and a source that serves a holding never needs it.

```java
@Component
class LoanReportEndpoint implements MountEndpoint {

    @Override
    public EndpointSpec spec() {
        return EndpointSpec.of("reports/loans.yaml", "application/yaml",
                "Loans", "What was borrowed in a window, most frequent first.",
                EndpointParam.select("dimension", List.of("topic", "source"),
                        "topic", "what to group by"),
                EndpointParam.optional("limit", ParamType.INTEGER, "20", "how many rows"));
    }

    @Override
    public void handle(CallContext ctx) {
        ctx.replyYaml(loans.count(ctx.text("dimension"), ctx.integer("limit", 20)));
    }
}

@Bean
FileSource files(LibraryFileSource holding, List<MountEndpoint> endpoints) {
    return new EndpointFileSource(holding, "What this mount holds.", endpoints);
}
```

`EndpointFileSource` keeps everything the wrapped source did — listings, reads,
writes, search — and adds the endpoints beside it, with the folders their paths
imply (`reports` above exists nowhere else, and without it the endpoint would
answer when addressed and be invisible to anyone browsing).

**Where those paths live is yours.** This module reserves no folder and suggests
none; the only path it claims is `_api.yaml`. `reports/` here is an example, not
a convention — pick whatever keeps clear of the holding you serve, since an
endpoint shadows a wrapped path of the same name (and says so in the log when it
does).

`CallContext` is the point of the exercise. It validates the whole query against
the declaration **before** `handle` runs: an undeclared name, a value of the
wrong type, a choice outside its set or a missing required parameter is a 400
with a reason. There is no way for an endpoint to read a parameter it did not
declare, and no way for a caller to send one that quietly does nothing.

Six names cannot be declared — `path`, `kind`, `entry`, `mode`, `caption` and
`download`. The first addresses the file on the wire; the rest are the reader's
own vocabulary (its reference grammar plus the content endpoint's disposition
switch) and are stripped before the query is forwarded. A parameter with one of
those names would never arrive, and nothing in your process could see why. The
list is refused at declaration time, so you find out while writing the endpoint
rather than from a read that quietly lost a parameter.

There are no annotations, no classpath scan, no path variables and no
interceptors. A variable in the path is where a declaration turns into a router,
and a mount that needs one address per instance has a real tree to put them in.

### Two formats of one report

A report served as both YAML and Markdown is two endpoints — the mime type
belongs to the path — but it should not be two entries in the description with
the same parameter list. The second one says whose answer it re-renders:

```java
return EndpointSpec.of("reports/loans.md", "text/markdown", "Loans",
                "The same report as a table, for reading.", SHARED_PARAMS)
        .asRenderingOf("reports/loans.yaml");
```

The description then carries one entry for the report, with the other format
under `alsoAt`. Both paths stay in the tree, both stay readable, both take the
same parameters — that last part is checked when the source is wrapped, because a
format described under someone else's parameter list while accepting a different
one is a documented lie. A rendering of a rendering is refused too: formats are
one level.

### `_api.yaml` — the mount describes itself

`EndpointFileSource` serves one more path: `_api.yaml` at the mount root, listing
every endpoint, every parameter and the calling convention in plain sentences.

It exists because parameterised views are findable through no listing, which makes
the declaration the only discovery channel there is. Serving it as a **file** is
what makes it usable without a contract change: an agent reads it with the
ordinary document tools, no new wire method, no new capability field.

It is deliberately **not listed** — it describes the mount rather than belonging
to it. The reader stats an unlisted mounted path on demand when it is addressed,
and the row it keeps is derived from the path, so it survives a listing that does
not mention it and comes back identical on the next read. The path is fixed
rather than configurable: a discoverable location that must first be discovered
is not one.

### The shared secret

Same rule as the other inbound modules — `api-key` empty means no check —
but **worth a second thought here specifically**: this endpoint serves file
*contents*, so an unguarded path is a file server. Set it unless something in
front of the application already decides who may read.

## Facets — being filtered

A **facet** is a dimension your source can be filtered by, and it works the same
way on both contracts. You declare it in your capabilities; a reader renders it
as a filter and sends the selection back.

```java
OdeFacet.tree("subject-place", "Where it is about", List.of(
        OdeFacetValue.of("m49:142", "Asia"),
        new OdeFacetValue("iso:SG", "Singapore", "m49:142")));
```

**Declaring one is a promise to apply it.** There is no "I can label this but
not query it" — the reader does no local facet filtering, so a facet you declare
and ignore is a filter that silently does nothing. If you cannot answer a
dimension, leave it out: a reader that selected it will skip you for that
request and say so, which is the honest outcome.

The selection travels as repeated `facet=<key>:<value>` query parameters on the
feed side and as a map in the body on the search side. It is split at the
**first** colon, because values contain colons themselves (`m49:142`, `iso:SG`).
Keys you never declared are dropped and logged rather than refused — a reader
may be newer than your end, and one filter it can live without should not turn
into a broken endpoint.

Two of the keys carry an agreed value system and mean the same thing across
sources: `origin-place` (where the *publisher* sits) and `subject-place` (what
the entry is *about*), both using `m49:` above the country level and `iso:` at
it. They are separate keys on purpose — a wire agency in London filing from
Singapore is both, differently. `origin-topic` and `subject-topic` are reserved
but have no agreed vocabulary yet, so they behave as source-specific keys. Any
other key is yours alone.

### Trees too large to travel inline

At most `OdeFacet.MAX_INLINE_VALUES` (500) values ride in the capabilities
response — it is cached, and it lands in every configuration form on the other
end. Past that, set `lazyChildren` and serve the tree one level at a time:

```java
@Override
public List<OdeFacetValue> facetValues(String key, @Nullable String parent) {
    return topics.childrenOf(parent);   // parent == null means the top level
}
```

`GET {path}/facets?key=…&parent=…` answers one level. For a facet whose values
did travel inline the endpoint answers from that list without consulting you —
still one level at a time, because `parent` is the question that was asked.

## Who may call

Every inbound module takes the same bearer token, and there are two ways to
decide whether it is good.

**One shared secret** (`api-key`, above) is enough for a source serving one
reader. It is not enough for anything else: it cannot be rotated without
downtime, cannot be revoked for one reader out of ten, and tells the source
nothing about who called.

**Your own validator** is the other way. Publish an `OdeAuthService` bean and
every Ode endpoint in the application authenticates through it:

```java
@Component
class TokenAuth implements OdeAuthService {

    @Override
    public OdeAuthDecision authenticate(String token, String endpointPath) {
        return subscriptions.find(token)
                .map(s -> s.active()
                        ? OdeAuthDecision.allow(
                                OdeCaller.of(s.customerId(), Map.of("plan", s.plan())))
                        : OdeAuthDecision.forbidden("subscription lapsed"))
                .orElseGet(OdeAuthDecision::unauthenticated);
    }
}
```

The token stays **opaque** to this contract — a row in a table, a JWT, a licence
key checked against a billing system. That is the point: an application that
already knows who its customers are uses that answer instead of learning a
second scheme.

Four rules the guard applies around it:

- **The service replaces the static comparison, it does not join it.** With the
  bean present, `api-key` is not consulted at all. Two parallel definitions of
  "valid" is the kind of thing that cannot be removed a year later, and the one
  that wins in an emergency is never the one you remember. The guard logs a
  warning if both are configured.
- **A request without a bearer header is refused before the service is
  called.** Deciding that no token at all is acceptable is a decision about
  whether the endpoint is public, and it is made by publishing the bean or not.
- **An exception is a refusal, never a pass.** A validator whose store is
  unreachable fails closed and the caller retries.
- **A service secures the path regardless of `api-key`.** Reading an unset
  property as "leave it open" would be the one failure mode that opens access
  rather than closing it.

`UNAUTHENTICATED` becomes a 401 and `FORBIDDEN` a 403 — different problems for
whoever has to fix them, and only the source can tell them apart. Neither
carries a body: the party being refused is the last one that should be told
which half of its credential was wrong.

### What the caller reaches

An allowed decision names an `OdeCaller`, and it arrives where the work happens
— `OdeSearchQuery.caller()`, `OdeItemQuery.caller()`, and as a parameter on the
on-demand fetches (`SearchSource.content(id, caller)`,
`FeedSource.body(id, reader, caller)`, `FeedSource.signal(request, caller)`,
each defaulting to the variant without it). Without that step, authenticating
would only be a doorman: a source cannot narrow what it serves to a caller it is
never told about.

Two boundaries hold:

- **`OdeCaller` is an installation, never a person.** It names the deployment
  whose token got in — a customer, a contract. Centauri's reader pseudonym is
  the other half of that distinction and stays separate: authorise with the
  caller, personalise with the pseudonym.
- **`capabilities()` must not depend on it.** Both ends cache that answer. Serve
  a caller fewer results, not a different declaration.

## Errors

One exception, `VanceOdeException`, with a `Kind` and `isRetryable()`.
A type per HTTP status would turn the caller's one real question — retry,
fix configuration, or give up — into a chain of instanceof checks.

| Kind | Retryable | Typical cause |
|---|---|---|
| `CONFIGURATION` | no | Ode unconfigured, or the event was never declared |
| `UNAUTHORIZED` | no | rejected bearer token |
| `NOT_FOUND` | no | unknown or disabled event |
| `REMOTE_FAILURE` | yes | the action itself failed |
| `TRANSPORT` | yes | brain unreachable |
| `PROTOCOL` | no | a 2xx that was not the expected shape |

On the **inbound** side — the endpoints you serve — a refusal carries
`OdeErrorResponse`, a short code and a sentence:

| Code | Status | Meaning |
|---|---|---|
| `bad_request` | 400 | the caller sent something this endpoint will not serve |
| `unauthorized` | 401 | missing or rejected bearer token |
| `read_only` | 405 | Jaglan only: a write against a source that does not take them |
| `bundle_too_large` | 413 | kit only: the kit packs to more than `max-bundle-bytes` |
| `source_failed` | 500 | your source threw; the reader is expected to back off |

The split matters more than it looks. A reader cools down on a 5xx and not on a
400, so a source that has fallen over must not answer 400 — it would keep being
asked at full rate. Throw `OdeBadRequestException` for your own refusals;
anything else that escapes is treated as the source failing.

## Requirements

- Java 21. Not 25 as in `vance` and `hrafnagud`: this is embedded in
  software we do not control, and the current LTS is the floor a consumer
  can be expected to meet.
- Spring Boot 4. Ode uses Jackson 3 (`tools.jackson.*`), which is what
  Boot 4 registers. Carrying both Jackson generations to stay compatible
  with Boot 3 would push the version conflict onto the consumer.

Ode does not inherit `spring-boot-starter-parent`; it imports the BOM. A
library should pin its own dependency versions without reaching into
anyone else's build.

## Licence

**Apache License 2.0** — see [LICENSE](LICENSE).

Deliberately different from its two neighbours, because it sits between
them and has a different job:

| Repo | Licence | Why |
|---|---|---|
| `vance` | Business Source License 1.1 | the product; protected |
| `vance-ode` | **Apache-2.0** | meant to be embedded in software we do not own |
| `hrafnagud` | GPLv3 | a consumer |

BSL 1.1 is not an open-source licence and is not GPL-compatible, so an
Ode under BSL could not legally be linked into `hrafnagud`. Copyleft
would have solved that but caused its own trouble: LGPL's
replace-the-library requirement is written for shared objects and fits
badly with a Spring Boot executable jar, and where an API boundary ends
in Java — implementing an SPI, inheriting auto-configuration — is
contested enough to make legal review a cost paid by exactly the adopters
this library wants.

Apache-2.0 also carries an explicit patent grant, which MIT does not.

Nothing is given away by this: Ode contains no brain code, only contracts
and transport. The value stays where it is protected, and the glue is
free — the usual arrangement for a commercial product's client SDK.
