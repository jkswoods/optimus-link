---
title: "Configuration"
---

# Configuration {#configuration}

The Hytale 0.7 support branch has no player configuration file or configurable power system. Its current limits are:

| Setting | Current behavior |
| --- | --- |
| Deployment and linking reach | 5 blocks |
| Lost-link recovery reach | 5 blocks |
| Control range | 128 blocks directly, or a route through linked Range Extenders with 128 blocks per hop |
| Active control | One robot per operator |
| Roster page | Three robots; additional robots use pagination |
| Ownership | Deploying player; no ownership-transfer feature |
| Remote world | Bound on first use; original world only |
| Operator body | Remains exposed under normal world rules |

Use the game's sound volume controls for mod cues. Generated deployment sounds play at the robot after successful deployment; remote feedback is private to the operator. Repeated feedback is limited. Human listening and real multiplayer sound checks remain pending.

A short servo cue follows accepted robot interactions. Mining and placement feedback requires an actual block change. These cues use the original generated robot sound family and do not loop.

The Assembly Dock performs crafting. It does not charge robots, consume fuel or schedule autonomous tasks.
