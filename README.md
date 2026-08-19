# Vance Ode

A Spring Boot library that connects foreign software to a
[Vancetope](https://github.com/mhus/vance) brain — in both directions.

Ode is what an application embeds when it wants to *use* a brain (fire an
event, get a translation back) or to *be used by* one (answer a search,
supply a feed). It carries the connection configuration, the error model
and the transport so the application does not re-derive them.

> **Status:** early. One subsystem implemented (`ursa`), two planned.
> **Licence:** Apache-2.0.

## Modules

One module per Vancetope subsystem the application takes part in.

| Module | Subsystem | Direction | Status |
|---|---|---|---|
| `vance-ode-core` | — | — | shared config, error model, HTTP transport |
| `vance-ode-ursa` | events / triggers | outbound | **implemented** |
| `vance-ode-centauri` | feed streams | inbound | planned |
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
