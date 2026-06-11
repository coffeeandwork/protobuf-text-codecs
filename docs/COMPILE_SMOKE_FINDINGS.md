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
| Go (jsonarray) | `} / else {` on separate lines (NaN/Inf branches, int64 parse fallbacks); nested types only flattened one level deep and referenced by bare name; proto3 optional enums/bytes mistyped; `math`/`strconv` imports missed for map keys/values | blockContinue pairing; `GoTypeMapper.simpleTypeName` flattens full path; pointer enums / plain `[]byte`; map-aware import checks |
| C# (both formats) | Nested enum collided with same-named property (CS0102); cross-namespace refs missing `using`; `AsReadOnly()` assigned to `List<T>`; top-level enums missing `using System;` | protobuf-standard `Types` wrapper + `Outer.Types.Inner` references; namespace-derived using directives; defensive copy without AsReadOnly |
| Rust (jsonarray) | `has_*` with `is_some()` on Vec/HashMap fields; cross-package refs unresolved; synthetic `*MapEntry` imports; base64 import missed for bytes map values; `&`-prefixed scalar reads in map pairs; recursive types had infinite size; aliased enums duplicated discriminants | has_* only for Option fields; `use crate::<pkg>::...` imports; map-entry skip; map-aware bytes check; deref-corrected reads; `Option<Box<T>>` for self-references; first-name-wins enums |

## Open (CI marked broken)

| Language | Finding |
|----------|---------|
| TypeScript | Cross-package references missing imports (`Cannot find name 'Address'`); references to nested types from other files import non-existent modules (`./Status.js`); non-string-keyed maps initialized as `{}` where pair-array `[K, V][]` is expected; deserializer passes `Record<string, any>` to pair-array parameters |
| Go | Cross-package references unsupported: Go imports need a module path, which requires honoring the `go_package` option (feature work). Same-package code (incl. nested types, optional enums/bytes, NaN handling) now compiles — see Fixed below |
| Rust (pbtk format only) | `PbtkRustGenerator` output has never compiled: 244 errors across 8 classes (unresolved modules, private methods called cross-module, missing imports, bad derefs). Best addressed by the planned pbtk generator restructuring (Phase F); jsonarray Rust passes |
| TypeScript | Cross-package references missing imports (`Cannot find name 'Address'`); references to nested types from other files import non-existent modules (`./Status.js`); non-string-keyed maps initialized as `{}` where pair-array `[K, V][]` is expected; deserializer passes `Record<string, any>` to pair-array parameters |
| Objective-C | Cross-file references to types with a different file prefix don't import/forward-declare (`PWInnerData` unknown in `PRWrapper.h`); proto2 enum properties boxed with `@()` on `id` type |
| C | Nested-type free-function name mismatch (`proto2test_Wrapper_inner_data_free` called, `proto2test_wrapper__inner_data_free` declared); same-named structs from different packages produce conflicting typedefs across headers |
| C++ | Cross-package references not declared (`Address`); recursive messages use incomplete types by value (`TreeNode` needs pointer indirection); `std::nullopt` assigned to non-optional members |
| Dart | Map fields import the synthetic `*MapEntry` file (same class as the fixed JS/TS bug) |
| Kotlin | Generated code references a `dev.protocgen...` runtime package that isn't a published Kotlin dependency |
| Zig | Cross-package references undeclared; `zig ast-check` treats unused parameters and never-mutated `var` as errors |
| PHP | **Passes** (first CI verdict, both formats) |

## Notes

- Ruby passes but warns: `allow_alias` enums emit duplicate hash keys
  (`key 1 is duplicated and overwritten`) — semantically harmless (alias),
  worth the same first-name-wins treatment as TypeScript.
- The cross-package/import class of bugs likely exists in most languages;
  test protos exercise it via `edgecases` ↔ `example` references.
