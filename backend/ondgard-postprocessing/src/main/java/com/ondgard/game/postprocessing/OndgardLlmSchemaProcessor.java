package com.ondgard.game.postprocessing;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@SupportedAnnotationTypes( "com.ondgard.game.postprocessing.OndgardLlmSchema" )
@SupportedSourceVersion( SourceVersion.RELEASE_25 )
@SupportedOptions( "ondgardLlmSchema.targetClass" )
public class OndgardLlmSchemaProcessor extends AbstractProcessor {

  private static final String DEFAULT_TARGET = "com.ondgard.game.config.ai.LlmResponseSchema";
  private static final String LLM_REQUIRED_FQCN = LlmRequired.class.getCanonicalName();

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    if( roundEnv.processingOver() || annotations.isEmpty() ){
      return false;
    }

    var elements = roundEnv.getElementsAnnotatedWith(OndgardLlmSchema.class);
    if( elements.isEmpty() ){
      return false;
    }

    var schemas = new ArrayList<SchemaConstant>();
    for( Element element : elements ){
      if( element.getKind() != ElementKind.CLASS && element.getKind() != ElementKind.RECORD ){
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
            "@OndgardLlmSchema can only be applied to classes and records", element);
        continue;
      }

      var typeElement = (TypeElement) element;
      var annotation = typeElement.getAnnotation(OndgardLlmSchema.class);
      String constantName = annotation.value() + "_SCHEMA";
      String schema = buildObjectSchema(typeElement, 0);
      schemas.add(new SchemaConstant(constantName, schema));
    }

    if( !schemas.isEmpty() ){
      writeSourceFile(schemas);
    }

    return true;
  }

  // ── Schema generation ──────────────────────────────────────────────

  private String buildObjectSchema(TypeElement typeElement, int indent) {
    var fields = getFields(typeElement);
    var sb = new StringBuilder();
    var pad = " ".repeat(indent);
    var innerPad = " ".repeat(indent + 2);
    var propPad = " ".repeat(indent + 4);

    sb.append("{\n");
    sb.append(innerPad).append("\"type\": \"OBJECT\",\n");
    sb.append(innerPad).append("\"properties\": {\n");

    var requiredFields = new ArrayList<String>();
    var iterator = fields.iterator();
    while( iterator.hasNext() ){
      var field = iterator.next();
      String name = field.getSimpleName().toString();
      TypeMirror type = field.asType();

      if( isRequired(field) ){
        requiredFields.add(name);
      }

      sb.append(propPad).append("\"").append(name).append("\": ");
      appendTypeSchema(sb, type, indent + 4);

      if( iterator.hasNext() ){
        sb.append(",");
      }
      sb.append("\n");
    }

    sb.append(innerPad).append("}");

    if( !requiredFields.isEmpty() ){
      sb.append(",\n");
      sb.append(innerPad).append("\"required\": [");
      sb.append(requiredFields.stream()
          .map(f -> "\"" + f + "\"")
          .collect(Collectors.joining(",")));
      sb.append("]");
    }

    sb.append("\n");
    sb.append(pad).append("}");

    return sb.toString();
  }

  private void appendTypeSchema(StringBuilder sb, TypeMirror type, int indent) {
    switch(type.getKind()){
      case BOOLEAN -> sb.append("{ \"type\": \"BOOLEAN\" }");
      case INT, LONG -> sb.append("{ \"type\": \"INTEGER\" }");
      case DOUBLE, FLOAT -> sb.append("{ \"type\": \"NUMBER\" }");
      case DECLARED -> appendDeclaredTypeSchema(sb, (DeclaredType) type, indent);
      default -> sb.append("{ \"type\": \"STRING\" }");
    }
  }

  private void appendDeclaredTypeSchema(StringBuilder sb, DeclaredType declaredType, int indent) {
    var typeUtils = processingEnv.getTypeUtils();
    var elementUtils = processingEnv.getElementUtils();

    var typeElement = (TypeElement) declaredType.asElement();
    String qualifiedName = typeElement.getQualifiedName().toString();

    // String
    if( qualifiedName.equals("java.lang.String") ){
      sb.append("{ \"type\": \"STRING\" }");
      return;
    }

    // Boxed primitives
    switch(qualifiedName){
      case "java.lang.Integer", "java.lang.Long" -> {
        sb.append("{ \"type\": \"INTEGER\" }");
        return;
      }
      case "java.lang.Double", "java.lang.Float" -> {
        sb.append("{ \"type\": \"NUMBER\" }");
        return;
      }
      case "java.lang.Boolean" -> {
        sb.append("{ \"type\": \"BOOLEAN\" }");
        return;
      }
    }

    // Collection / List / Set → ARRAY
    var collectionElement = elementUtils.getTypeElement("java.util.Collection");
    if( collectionElement != null ){
      TypeMirror collectionErasure = typeUtils.erasure(collectionElement.asType());
      TypeMirror typeErasure = typeUtils.erasure(declaredType);
      if( typeUtils.isAssignable(typeErasure, collectionErasure) ){
        appendArraySchema(sb, declaredType, indent);
        return;
      }
    }

    // Nested OBJECT (recursive)
    sb.append(buildObjectSchema(typeElement, indent));
  }

  private void appendArraySchema(StringBuilder sb, DeclaredType collectionType, int indent) {
    var innerPad = " ".repeat(indent + 2);
    var closePad = " ".repeat(indent);

    List<? extends TypeMirror> typeArgs = collectionType.getTypeArguments();
    TypeMirror itemType = typeArgs.isEmpty() ? null : typeArgs.get(0);

    sb.append("{\n");
    sb.append(innerPad).append("\"type\": \"ARRAY\",\n");
    sb.append(innerPad).append("\"items\": ");

    if( itemType != null ){
      appendTypeSchema(sb, itemType, indent + 2);
    } else{
      sb.append("{ \"type\": \"STRING\" }");
    }

    sb.append("\n");
    sb.append(closePad).append("}");
  }

  // ── Field introspection ────────────────────────────────────────────

  private List<? extends Element> getFields(TypeElement typeElement) {
    // Always use FIELD elements (not record components) so that annotations
    // like @JsonProperty — whose @Target does not include RECORD_COMPONENT —
    // are visible via getAnnotationMirrors().
    return typeElement.getEnclosedElements().stream()
        .filter(e -> e.getKind() == ElementKind.FIELD)
        .filter(e -> !e.getModifiers().contains(Modifier.STATIC))
        .toList();
  }

  private boolean isRequired(Element element) {
    for( AnnotationMirror mirror : element.getAnnotationMirrors() ){
      if( mirror.getAnnotationType().toString().equals(LLM_REQUIRED_FQCN) ){
        return true;
      }
    }
    return false;
  }

  // ── Source file generation ─────────────────────────────────────────

  private void writeSourceFile(List<SchemaConstant> schemas) {
    String targetClass = processingEnv.getOptions()
        .getOrDefault("ondgardLlmSchema.targetClass", DEFAULT_TARGET);

    int lastDot = targetClass.lastIndexOf('.');
    String packageName = targetClass.substring(0, lastDot);
    String className = targetClass.substring(lastDot + 1);

    try{
      JavaFileObject sourceFile = processingEnv.getFiler().createSourceFile(targetClass);
      try( var writer = new PrintWriter(sourceFile.openWriter()) ){
        writer.println("package " + packageName + ";");
        writer.println();
        writer.println("final class " + className + " {");
        writer.println();
        writer.println("  private " + className + "() {}");

        for( var schema : schemas ){
          writer.println();
          writer.println("  static final String " + schema.name + " = \"\"\"");
          for( String line : schema.json.split("\n", -1) ){
            writer.println("      " + line);
          }
          writer.println("      \"\"\";");
        }

        writer.println("}");
      }
    }catch(IOException e){
      processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
          "Failed to write generated source file: " + e.getMessage());
    }
  }

  private record SchemaConstant( String name, String json ) {
  }
}
