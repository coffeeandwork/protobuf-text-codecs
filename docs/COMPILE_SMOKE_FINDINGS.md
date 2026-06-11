# Compile-Smoke Findings

`integration-tests/compile-smoke.sh <lang>` generates code for one language
(both formats, all 5 test protos) and checks it with the language's real
toolchain. The CI matrix (`generated-code.yml`) runs it for all 17 languages;
languages with open findings are marked `broken: true` (run, but don't fail CI).

Status as of 2026-06-11.

## Fixed

| Language | Finding | Fix |
|----------|---------|-----|
| Java (both formats) | Repeated/map deserialization called `builder.setX(list/map)`; builders only have indexed `setX(int, v)` | Emit `addAllX`/`putAllX` |
| Java (both formats) | Cross-package type references had no `import` statements | `TypeRegistry.getDefiningFile()` + `JavaImportUtil` emits imports |
| Java (both formats) | `appendJsonArray`/`fromJsonArray`/`appendPbtkFields`/`countPbtkFields`/`parsePbtkTokens` were package-private, breaking cross-package field access | Made public |
| Rust | Recursive message imported itself (`use super::tree_node::TreeNode` inside `tree_node.rs`, E0255) | Skip self-imports |
| Perl (jsonarray) | `JSON::true`/`JSON::false` barewords with only `JSON::PP` imported (strict failure) | `JSON::PP::true()`/`false()` |
| Perl (pbtk) | Constructors emitted literal `%%args` | `%args` |
| JS/TS | Map fields imported the synthetic `*MapEntry` message (no such file); recursive messages imported themselves | Skip both |
| TypeScript | `allow_alias` enums emitted duplicate reverse-mapping keys (TS1117) | First name wins; union deduplicated |

## Open (CI marked broken)

| Language | Finding |
|----------|---------|
| TypeScript | Cross-package references missing imports (`Cannot find name 'Address'`); references to nested types from other files import non-existent modules (`./Status.js`); non-string-keyed maps initialized as `{}` where pair-array `[K, V][]` is expected; deserializer passes `Record<string, any>` to pair-array parameters |
| Go | Structural syntax errors in `kitchen_sink.go` and others: `unexpected keyword else`, statements outside function bodies, methods without receivers |
| Rust | Map presence checked with `.is_some()` on a `HashMap`; cross-module references missing `use` paths; `base64::engine::general_purpose` not imported; type-annotation gaps |
| C# | Nested enum named like a sibling member collides (`KitchenSink` already contains a definition for `Status`); cross-namespace references missing `using` |
| Objective-C | Cross-file references to types with a different file prefix don't import/forward-declare (`PWInnerData` unknown in `PRWrapper.h`); proto2 enum properties boxed with `@()` on `id` type |
| Zig / Dart / PHP / Kotlin | Not yet verified locally (no toolchain); CI will produce first results |

## Notes

- Ruby passes but warns: `allow_alias` enums emit duplicate hash keys
  (`key 1 is duplicated and overwritten`) — semantically harmless (alias),
  worth the same first-name-wins treatment as TypeScript.
- The cross-package/import class of bugs likely exists in most languages;
  test protos exercise it via `edgecases` ↔ `example` references.
