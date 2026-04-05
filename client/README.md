# JossDoubleJump — client jump-edge channel (optional)

This folder is a **placeholder** for a Hytale **client** mod that sends jump DOWN/UP edges over the same transport as the server’s `PageManagerMixin` hook (`CustomPageEvent.data` with prefix `jdj:v1:`).

## Wire format

- Prefix: `jdj:v1:`
- JSON body: `{"v":1,"seq":<long>,"t":"DOWN"|"UP"}`
- `seq` must be strictly increasing per session so the server can drop duplicates.

## Proof of life (server)

1. Install **server** JAR only → run `/jossedgetest` (Creative) → next airborne tick should log `[JDJ edge] transport proof:` once and accept the synthetic edge.
2. With a **real client** mod sending `CustomPageEvent` as above, confirm the same log and `signalSrc=edgeChannel` when `traceJumpAuthorityDiagnostics` is true.

## Implementation notes

- Bind to the **game’s jump control** only (no new keybind).
- Send **only** on false→true (DOWN) and true→false (UP); do not send while held.
- Use `com.hypixel.hytale.protocol.packets.interface_.CustomPageEvent` with `CustomPageEventType.Data` (verify enum name in your protocol version).

The shared helper `JumpEdgeWireFormat` (if present) builds the payload string without server APIs.
