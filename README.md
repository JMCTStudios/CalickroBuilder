# CalickroBuilder

Foundation build for your NPC builder plugin.

## CalickroSMP
- Paper 1.21.11 / Java 21 plugin skeleton
- `/cali builder` command tree
- builder registry and job manager
- provider abstraction layer for Citizens / FancyNPCs / future providers
- build plan / build spec model
- preflight validation pipeline
- WorldGuard / GriefPrevention hook placeholders
- YAML config scaffolding

## What this starter intentionally does not do yet
- real Citizens trait registration
- real FancyNPCs integration
- real WorldGuard / GriefPrevention API calls
- procedural house generation
- actual block placement
- AI prompt parsing

This foundation is set up so those can be dropped in cleanly instead of becoming one giant messy class.
