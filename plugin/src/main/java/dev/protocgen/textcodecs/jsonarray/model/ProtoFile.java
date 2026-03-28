package dev.protocgen.textcodecs.jsonarray.model;

import java.util.List;

/** Language-neutral representation of a .proto file's metadata and contents. */
public class ProtoFile {

  private final String fileName;
  private final String protoPackage;
  private final String javaPackage; // from java_package option, may be null
  private final String javaOuterClassname; // from java_outer_classname option, may be null
  private final String syntax; // "proto2" or "proto3"
  private final List<ProtoMessage> messages;
  private final List<ProtoEnum> enums;
  private final List<String> dependencies; // imported file names

  public ProtoFile(
      String fileName,
      String protoPackage,
      String javaPackage,
      String javaOuterClassname,
      String syntax,
      List<ProtoMessage> messages,
      List<ProtoEnum> enums,
      List<String> dependencies) {
    this.fileName = fileName;
    this.protoPackage = protoPackage;
    this.javaPackage = javaPackage;
    this.javaOuterClassname = javaOuterClassname;
    this.syntax = syntax;
    this.messages = List.copyOf(messages);
    this.enums = List.copyOf(enums);
    this.dependencies = List.copyOf(dependencies);
  }

  public String getFileName() {
    return fileName;
  }

  public String getProtoPackage() {
    return protoPackage;
  }

  public String getJavaPackage() {
    return javaPackage;
  }

  public String getJavaOuterClassname() {
    return javaOuterClassname;
  }

  public String getSyntax() {
    return syntax;
  }

  public List<ProtoMessage> getMessages() {
    return messages;
  }

  public List<ProtoEnum> getEnums() {
    return enums;
  }

  public List<String> getDependencies() {
    return dependencies;
  }

  public boolean isProto3() {
    return "proto3".equals(syntax);
  }

  public boolean isProto2() {
    return "proto2".equals(syntax);
  }
}
