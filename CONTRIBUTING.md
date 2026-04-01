# Contributing to protobuf-text-codecs

Thank you for your interest in contributing! This guide covers how to build, test, and submit changes.

## Prerequisites

- **Java 17+** (the plugin is written in Java)
- **Gradle** (wrapper included -- use `./gradlew`)
- **protoc** (Protocol Buffers compiler, version 3.x+)

## Building

Build both plugin fat JARs with Shadow:

```bash
./gradlew :plugin:shadowJar :plugin:pbtkShadowJar
```

This produces:
- `plugin/build/libs/protoc-gen-jsonarray.jar` — JSON array encoding plugin
- `plugin/build/libs/protoc-gen-pbtkurl.jar` — pbtk URL encoding plugin

The wrapper scripts at the repository root (`protoc-gen-jsonarray`, `protoc-gen-pbtkurl`) invoke these JARs.

## Running Tests

```bash
# Run all tests
./gradlew test

# Run only plugin unit tests
./gradlew :plugin:test

# Run a specific test class
./gradlew :plugin:test --tests "dev.protocgen.textcodecs.jsonarray.JavaCodeGenTest"
```

### Golden-File Tests

Generator output is verified against golden files (`*.approved` snapshots). When you intentionally change generator behavior:

1. Run the tests -- they will fail and show a diff.
2. Review the diff to confirm the change is correct.
3. Update the golden files with the `--update-golden` flag.

## Code Formatting

The project uses [Spotless](https://github.com/diffplug/spotless) with **Google Java Format** to enforce the [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html).

```bash
# Check formatting (CI will fail if this fails)
./gradlew spotlessCheck

# Auto-fix formatting
./gradlew spotlessApply
```

Always run `./gradlew spotlessApply` before committing.

## Code Style

Generated code should follow the target language's idiomatic style guide so it integrates naturally into user projects.

**Plugin source code (Java):**
- [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html) -- enforced by Spotless with Google Java Format

**Generated code -- languages with Google style guides:**

| Language | Style Guide |
|----------|-------------|
| Java | [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html) |
| Python | [Google Python Style Guide](https://google.github.io/styleguide/pyguide.html) |
| C/C++ | [Google C++ Style Guide](https://google.github.io/styleguide/cppguide.html) |
| JavaScript | [Google JavaScript Style Guide](https://google.github.io/styleguide/jsguide.html) |
| TypeScript | [Google TypeScript Style Guide](https://google.github.io/styleguide/tsguide.html) |
| Go | [Google Go Style Guide](https://google.github.io/styleguide/go/) |
| Swift | [Google Swift Style Guide](https://google.github.io/swift/) |
| Objective-C | [Google Objective-C Style Guide](https://google.github.io/styleguide/objcguide.html) |
| Shell scripts | [Google Shell Style Guide](https://google.github.io/styleguide/shellguide.html) |

**Generated code -- languages using community standards:**

| Language | Style Guide |
|----------|-------------|
| Kotlin | [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html) |
| C# | [Microsoft C# Coding Conventions](https://learn.microsoft.com/en-us/dotnet/csharp/fundamentals/coding-style/coding-conventions) |
| Dart | [Effective Dart Style](https://dart.dev/effective-dart/style) |
| PHP | [PSR-12 Extended Coding Style](https://www.php-fig.org/psr/psr-12/) |
| Ruby | [Ruby Style Guide](https://rubystyle.guide/) |
| Rust | [Rust API Guidelines](https://rust-lang.github.io/api-guidelines/) |
| Zig | [Zig Style Guide](https://ziglang.org/documentation/master/#style-guide) |
| Perl | [perlstyle](https://perldoc.perl.org/perlstyle) |

## Testing with protoc

To manually test the plugins with `protoc`:

```bash
# Build both plugins first
./gradlew :plugin:shadowJar :plugin:pbtkShadowJar

# Make the wrapper scripts executable
chmod +x protoc-gen-jsonarray protoc-gen-pbtkurl

# JSON array format
protoc \
  --plugin=protoc-gen-jsonarray=./protoc-gen-jsonarray \
  --jsonarray_out=lang=java:./out-jsonarray \
  test-protos/src/main/proto/user.proto

# pbtk URL format
protoc \
  --plugin=protoc-gen-pbtkurl=./protoc-gen-pbtkurl \
  --pbtkurl_out=lang=java:./out-pbtkurl \
  test-protos/src/main/proto/user.proto

# Inspect the generated output
ls out-jsonarray/ out-pbtkurl/
```

Replace `lang=java` with any supported language identifier: `java`, `python`, `javascript` (alias: `js`), `typescript` (alias: `ts`), `c`, `cpp` (alias: `c++`), `rust`, `zig`, `go`, `csharp` (alias: `c#`), `kotlin` (alias: `kt`), `swift`, `dart`, `php`, `ruby` (alias: `rb`), `objc` (alias: `objective-c`), `perl`.

## Adding a New Language Generator

To add support for a new target language:

1. **JSON array format:** Create a new package under `plugin/src/main/java/dev/protocgen/textcodecs/jsonarray/codegen/<language>/` with 6 classes:
   - **`<Lang>Generator`** -- implements `LanguageGenerator`
   - **`<Lang>NameResolver`** -- implements `NameResolver`
   - **`<Lang>TypeMapper`** -- implements `TypeMapper`
   - **`<Lang>CodeEmitter`** -- generates complete source files
   - **`<Lang>SerializerGenerator`** -- generates the serialize method body
   - **`<Lang>DeserializerGenerator`** -- generates the deserialize method body

2. **pbtk URL format:** Create a corresponding package under `plugin/src/main/java/dev/protocgen/textcodecs/pbtkurl/codegen/<language>/` with a `Pbtk<Lang>Generator` that reuses the NameResolver and TypeMapper from step 1.

3. Register both generators:
   - JSON array: in `PluginRunner.java`
   - pbtk URL: in `PbtkPluginRunner.java`

4. Add tests: golden-file tests, parameterized multi-language tests, and integration tests.

5. If the language benefits from a shared runtime library, add it under `runtime/<language>/`.

Refer to existing packages (e.g., `codegen/java/` for 6-class modular pattern, or `pbtkurl/codegen/go/` for monolithic pattern) as references.

## Pull Request Process

1. **Fork** the repository and create a feature branch from `main`.
2. **Make your changes** -- keep commits focused and well-described.
3. **Run all tests** and ensure they pass:
   ```bash
   ./gradlew test
   ```
4. **Format your code**:
   ```bash
   ./gradlew spotlessApply
   ```
5. **Verify formatting passes**:
   ```bash
   ./gradlew spotlessCheck
   ```
6. **Submit a pull request** against `main` with a clear description of your changes.

## Reporting Issues

If you find a bug or have a feature request, please open an issue on GitHub. Include:

- The proto file(s) that reproduce the issue
- The `protoc` command you ran
- The expected vs. actual output
- Your `protoc` version (`protoc --version`) and Java version (`java -version`)
