/*
 * Copyright 2024 protobuf-text-codecs contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.protocgen.textcodecs.jsonarray.codegen;

import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse;
import dev.protocgen.textcodecs.jsonarray.model.ProtoFile;
import dev.protocgen.textcodecs.jsonarray.model.TypeRegistry;
import java.util.List;

/**
 * Interface for language-specific code generators. Each target language implements this to produce
 * {@link CodeGeneratorResponse.File} entries from analyzed proto definitions. The returned files
 * are assembled into the {@link CodeGeneratorResponse} that protoc writes to disk.
 *
 * <p>See the <a
 * href="https://protobuf.dev/reference/cpp/api-docs/google.protobuf.compiler.plugin.pb/#CodeGeneratorResponse.File">CodeGeneratorResponse.File
 * docs</a> for the file name and content fields.
 */
public interface LanguageGenerator {

  /** Generate source files for all messages and enums in the given proto file. */
  List<CodeGeneratorResponse.File> generate(ProtoFile file, TypeRegistry registry);
}
