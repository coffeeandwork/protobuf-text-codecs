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
| TypeScript/JavaScript (both formats) | Cross-package and nested-type references had no resolvable imports; nested messages deeper than one level were dropped; maps typed as pair arrays but stored as objects; optional scalars failed strict null checks | Path-aware import specs via `JsImportUtil`; recursive nested emission/exports; `Record<string, V>` map typing; non-null assertions under presence guards |
| Dart (both formats) | Synthetic `*MapEntry` imports; no cross-package import paths; nested messages deeper than one level dropped | Shared path-aware collector in `DartCodeEmitter`; recursive nested emission |
| Objective-C (both formats) | Nested-type references used a bogus prefix (last-dot split); oneof case property never declared and serializer referenced a phantom `<group>Value` property; enum properties typed `id`; cross-package/nested-container imports missing; deep-nested forward declarations missing; pbtk codec methods undeclared in headers | Package/type segment split in resolveTypeReference; case property + member-property serialization; NSInteger enums; container-header and package-path imports; recursive forward decls; header declarations |
| Zig (both formats) | Cross-package references undeclared; synthetic `*MapEntry` imports; recursive messages imported themselves; `zig ast-check` strictness: unused params, never-mutated `var`, shadowed locals, redeclared iterators, missing switch-prong commas; base64 decode allocated with the outer array length (real bug exposed by un-shadowing) | Path-aware relative imports; unique per-field locals; conditional discards and const bindings; prong commas; decoded-length alloc |
| C (jsonarray) | Function-prefix derivation mismatched declarations for nested types; anonymous-struct typedefs conflicted with named forward declarations; repeated enum arrays typed `void**`; optional string/bytes serialized through the numeric path; oneof bytes had no length storage; nested enum and map-entry references produced includes of nonexistent headers; deep-nested typedefs missing | Package/type segment prefixes; named struct tags with forward typedefs; enum-typed arrays; string/bytes cases in the optional path; struct-level `<member>_len` siblings; container-header includes; recursive typedefs |
| C (pbtk format only) | Cross-package/nested-container includes pointed at nonexistent headers (synthetic `*MapEntry` and nested-enum/nested-type names resolved as top-level files); nested messages deeper than one level were never forward-declared; struct bodies used anonymous `typedef struct {…}` that conflicted with forward typedefs and broke recursive self-references; oneof bytes members and bytes map values lacked length storage | Ported the jsonarray `CCodeEmitter` fixes into `PbtkCGenerator`: map-entry-skipping, top-level-container and cross-package package-directory includes; recursive nested forward typedefs; named struct tags with preceding forward typedef; external forward declarations; struct-level `<member>_len` siblings for oneof bytes and `value_len` for bytes map values |
| C++ (both formats) | Cross-package references emitted unqualified and never declared; nested messages deeper than one level dropped; synthetic `*MapEntry` and nested-enum (`Status`) includes pointed at nonexistent headers; recursive singular message fields used `std::optional<T>` of an incomplete type; jsonarray oneof serializer referenced `__oneof_*_case_` instead of `pb_oneof_*_case_`; pbtk `pbtk_detail_` helper namespace was unnamed and `<vector>` was missing for the always-emitted base64 helpers | Namespace-qualified type names + path-aware cross-package/same-package includes via shared `CppTypeUtil`; flattened recursive nested-message/enum emission deepest-first; map-entry/internal-type include skips; `std::shared_ptr<T>` indirection for self-references; corrected oneof case-field name; per-class `pbtk_detail_<Class>` namespace set during emission; always include `<vector>` |

## Open (CI marked broken)

| Language | Finding |
|----------|---------|
| Go | Cross-package references unsupported: Go imports need a module path, which requires honoring the `go_package` option (feature work). Same-package code (incl. nested types, optional enums/bytes, NaN handling) now compiles — see Fixed below |
| Rust (pbtk format only) | `PbtkRustGenerator` output has never compiled: 244 errors across 8 classes (unresolved modules, private methods called cross-module, missing imports, bad derefs). Best addressed by the planned pbtk generator restructuring (Phase F); jsonarray Rust passes |
| Kotlin | Generated code references a `dev.protocgen...` runtime package that isn't a published Kotlin dependency |

PHP passed its first CI verdict (both formats) and is no longer marked broken.

## Notes

- Ruby passes but warns: `allow_alias` enums emit duplicate hash keys
  (`key 1 is duplicated and overwritten`) — semantically harmless (alias),
  worth the same first-name-wins treatment as TypeScript.
- The cross-package/import class of bugs likely exists in most languages;
  test protos exercise it via `edgecases` ↔ `example` references.
