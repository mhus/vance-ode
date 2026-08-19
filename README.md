# Vance Ode

A Spring Boot library that connects foreign software to a
[Vancetope](https://github.com/mhus/vance) brain — in both directions.

Ode is what an application embeds when it wants to *use* a brain (fire an
event, get a translation back) or to *be used by* one (answer a search,
supply a feed). It carries the connection configuration, the error model
and the transport so the application does not re-derive them.

> **Status:** early. Two subsystems implemented (`ursa`, `centauri`), one planned.
> **Licence:** Apache-2.0.

## Modules

One module per Vancetope subsystem the application takes part in.

| Module | Subsystem | Direction | Status |
|---|---|---|---|
| `vance-ode-core` | — | — | shared config, error model, HTTP transport |
| `vance-ode-ursa` | events / triggers | outbound | **implemented** |
| `vance-ode-centauri` | feed streams | inbound | **implemented** |
| `vance-ode-zarniwoop` | research / search | inbound | planned |

### Why by subsystem and not by direction

The obvious alternative is to split client from server, and it was the
first thing tried. It does not survive contact with the actual
subsystems: two of the three are **inbound** contracts, where the
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

`vance-ode-core` must stay free of Spring Web. It is what keeps the
outbound-only case lean, and that only holds as long as nothing puts a
controller in it.

## Using it

```xml
<dependency>
    <groupId>de.mhus.vance.ode</groupId>
    <artifactId>vance-ode-ursa</artifactId>
    <version>0.1.0-SNAPSHOT</version>
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
    <version>0.1.0-SNAPSHOT</version>
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
| `POST {path}/signal` | the back channel (see below) |

Timestamps are ISO-8601 instants, the capabilities TTL an ISO-8601 duration —
self-describing matters more between two systems than brevity.

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
