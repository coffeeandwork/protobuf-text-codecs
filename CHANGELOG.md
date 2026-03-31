# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.2.0] - 2026-03-26

### Added
- New `protoc-gen-pbtkurl` plugin for pbtk URL encoding (Google Maps protobuf text format)
- pbtk URL format: `!<field_number><type_char><value>` — compact, URL-safe, self-describing
- Type characters: `s`=string (URL-encoded), `i`=integer, `d`=double, `f`=float, `b`=bool (0/1), `e`=enum, `m`=message, `z`=bytes (base64)
- Nested messages use `!<num>m<count>` prefix followed by sub-fields
- Repeated fields emit one token per element; maps serialize as repeated `!<num>m2!1<key>!2<val>` entries
- No JSON library dependency — generated pbtk code uses pure string manipulation
- All 17 target languages supported: Java, Python, JavaScript, TypeScript, C, C++, Rust, Zig, Go, C#, Kotlin, Swift, Dart, PHP, Ruby, Objective-C, Perl
- 8 new language generators: C# (System.Text.Json), Kotlin (kotlinx.serialization), Swift (Foundation), Dart (dart:convert), PHP (json built-in), Ruby (json stdlib), Objective-C (Foundation), Perl (JSON CPAN)
- Language aliases: `c#`=csharp, `kt`=kotlin, `rb`=ruby, `objective-c`=objc
- KeywordUtil expanded to 16 keyword sets (~950 lines) covering all 17 languages
- Separate shadow JAR build task (`pbtkShadowJar`) and wrapper script (`protoc-gen-pbtkurl`)
- Shares infrastructure with jsonarray plugin: model, MessageAnalyzer, TypeRegistry, NameResolvers, TypeMappers, KeywordUtil
- Multi-language parameterized tests for pbtk code generation

### Changed
- Project renamed from `protoc-gen-jsonarray` to `protobuf-text-codecs` to reflect multi-format support
- Several package-private methods made public to support cross-package reuse (`snakeToPascal`, `pascalToSnake`, `toSnakeCase`, `simpleTypeName`)

### Removed
- Jackson dependency eliminated from generated Java code; replaced with zero-dependency built-in `JsonArrayWriter` and `JsonArrayReader`
- `serialize(ObjectMapper)` and `deserialize(ArrayNode, ObjectMapper)` API methods removed in favor of `toJsonString()`/`fromJsonString()`

## [0.1.0] - 2026-03-19

### Added
- Initial release of protoc-gen-jsonarray
- Positional JSON array encoding for Protocol Buffer messages, where array index corresponds to field number
- Code generation for 9 target languages: Java, Python, JavaScript, TypeScript, C, C++, Rust, Zig, and Go
- Both proto2 and proto3 syntax support, including proto2 required fields, optional fields with schema-specified defaults, and groups
- Full scalar type support: string, int32/64, uint32/64, sint32/64, fixed32/64, sfixed32/64, float, double, bool, bytes (Base64-encoded)
- Enum support with integer-value serialization
- Nested message support with recursive JSON array encoding
- Repeated field support serialized as JSON arrays
- Map field support: JSON objects for string-keyed maps, array-of-pairs for non-string-keyed maps
- Oneof field support with positional null slots for inactive members
- Proto3 optional (explicit presence) support with null serialization for unset values
- Well-known type support: Timestamp (RFC 3339), Duration, BoolValue/Int32Value/StringValue and other wrapper types, Struct, Value, ListValue, FieldMask, and Empty
- Rejection of google.protobuf.Any at generation time with a clear error message
- Schema evolution via field-number-based positioning: adding fields appends to the array, removing fields leaves null slots, short arrays on deserialization use default values
- Security: field name validation against `[a-zA-Z_][a-zA-Z0-9_]*` pattern to reject crafted requests
- Security: output path traversal protection rejecting paths containing `..`
- Cross-language round-trip compatibility: data serialized in any language can be deserialized by any other
- Runtime libraries for Java (Jackson-based), C (cJSON-based), C++ (nlohmann/json header-only), and Rust (serde_json crate)
- Self-contained generated code for Python, JavaScript, TypeScript, Zig, and Go (no runtime library needed)
- Language-neutral internal model with abstract template-method base classes for code generation
- Golden-file snapshot testing for all generators
- Integration tests with kitchen_sink.proto exercising every field type
- Google Java Style compliance enforced by Spotless with Google Java Format
- Wrapper shell script for easy protoc invocation

[0.2.0]: https://github.com/coffeeandwork/protobuf-text-codecs/releases/tag/v0.2.0
[0.1.0]: https://github.com/coffeeandwork/protobuf-text-codecs/releases/tag/v0.1.0
