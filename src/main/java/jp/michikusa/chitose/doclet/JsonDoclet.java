package jp.michikusa.chitose.doclet;

import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.ConstructorDoc;
import com.sun.javadoc.FieldDoc;
import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.RootDoc;
import com.sun.javadoc.SeeTag;
import com.sun.javadoc.Tag;
import com.sun.source.doctree.*;
import com.sun.source.util.*;
import java.io.IOException;
import java.io.OutputStream;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import java.util.Locale;
import java.util.Set;
import java.util.stream.*;
import java.util.function.*;
import javax.lang.model.element.Element;
import static javax.lang.model.type.TypeKind.*;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic.Kind;
import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.doclet.Reporter;

/**
 * Doclet implementation for javadoc command. This output is based on JavaScript Object Notation.
 *
 * @author E.Sekito
 * @since 2014/07/31
 */
public class JsonDoclet implements Doclet {

  private Reporter reporter;

  private DocTrees docTrees;

  @Override
  public void init(Locale locale, Reporter reporter) {
    this.reporter = reporter;
    reporter.print(Kind.NOTE, "Initialised JsonDoclet");
  }

  @Override
  public String getName() {
    return "JSON Doclet";
  }

  @Override
  public Set<? extends Option> getSupportedOptions() {
    return Set.of(DocletOptions.OUTPUT_FILE, DocletOptions.APPEND, DocletOptions.PRETTY);
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.RELEASE_5;
  }

  @Override
  public boolean run(DocletEnvironment docletEnvironment) {
    reporter.print(Kind.NOTE, "HELLO WORLD!");

    OutputStream ostream = null;
    JsonGenerator generator = null;
    try {
      ostream = DocletOptions.openOutputStream();
      generator = new JsonFactory().createGenerator(ostream);

      if (DocletOptions.isPretty()) {
        generator.setPrettyPrinter(new DefaultPrettyPrinter());
      }

      docTrees = docletEnvironment.getDocTrees();

      write(generator, docletEnvironment);

      generator.flush();
    } catch (IOException e) {
      throw new RuntimeException(e);
    } finally {
      if (ostream != null) {
        try {
          ostream.close();
        } catch (IOException e) { /* TODO */ }
      }
      if (generator != null) {
        try {
          generator.close();
        } catch (IOException e) { /* TODO */ }
      }
    }

    return true;
  }

  void write(JsonGenerator g, DocletEnvironment docEnv)
      throws IOException {
    g.writeStartObject();
    {
      g.writeArrayFieldStart("classes");

      for (final Element classDoc : ElementFilter.typesIn(docEnv.getIncludedElements())) {
        writeClass(g, (TypeElement) classDoc);
      }

      g.writeEndArray();
    }
    g.writeEndObject();
  }

  static boolean hasElement(TypeMirror mirror) {
    switch (mirror.getKind()) {
      case DECLARED:
      case ERROR:
      case TYPEVAR:
        return true;
      default:
        return false;
    }
  }

  static Element getElement(TypeMirror mirror) {
    switch (mirror.getKind()) {
      case DECLARED:
        return ((DeclaredType) mirror).asElement();
      case ERROR:
        return ((ErrorType) mirror).asElement();
      case TYPEVAR:
        return ((TypeVariable) mirror).asElement();
      default:
        throw new IllegalArgumentException("TypeMirror subclass doesn't have an element");
    }
  }

  static interface UnsafeConsumer<T, E extends Throwable> {
    void accept(T t) throws E;
  }

  static <T, E extends Throwable> Consumer<T> unsafe(UnsafeConsumer<T, E> unsafe) {
    return t -> {
      try {
        unsafe.accept(t);
      } catch (Throwable e) {
        throw new RuntimeException(e);
      }
    };
  }

  void writeClass(JsonGenerator g, TypeElement type)
      throws IOException {
    g.writeStartObject();

    g.writeObjectField("name", type.getQualifiedName().toString());
    {
      g.writeArrayFieldStart("interfaces");

      type.getInterfaces().stream()
        .filter(i -> hasElement(i))
        .map(i -> getElement(i))
        .filter(TypeElement.class::isInstance)
        .map(TypeElement.class::cast)
        .forEach(unsafe(iface -> g.writeString(iface.getQualifiedName().toString()))); 

      g.writeEndArray();
    }
    TypeMirror superType = type.getSuperclass();
    g.writeObjectField("superclass",
        (superType != null && hasElement(superType) ? ((TypeElement) getElement(superType)).getQualifiedName().toString() : ""));
    
    System.out.println("Name was " + type.getQualifiedName());
    DocCommentTree doc = docTrees.getDocCommentTree(type);
    if (doc == null) return;
    String summary = doc.getFullBody().stream().map(DocTree::toString).collect(Collectors.joining(""));
    doc.getBlockTags().stream().filter(doc -> doc.getKind() == DocTree.Kind.SINCE).findFirst();
    /*g.writeObjectField("comment_text", doc.commentText);
    {
      final Tag tag = get(doc.tags("since"), 0);

      g.writeObjectField("since", (tag != null) ? tag.text() : "");
    }*/
    Stream<? extends DocTree> seeTags = doc.getBlockTags().stream().filter(doc -> doc.getKind() == DocTree.Kind.SEE);
    /*
    {
      g.writeArrayFieldStart("see");

      for (final SeeTag tag : doc.seeTags()) {
        g.writeString(tag.referencedClassName());
      }

      g.writeEndArray();
    }*/

    List<? extends Element> members = type.getEnclosedElements();
    List<ExecutableElement> constructors = ElementFilter.constructorsIn(members);
    {
      g.writeArrayFieldStart("constructors");

      for (final ExecutableElement ctor : constructors) {
        writeConstructor(g, ctor);
      }

      g.writeEndArray();
    }
    List<VariableElement> fields = ElementFilter.fieldsIn(members);
    {
      g.writeArrayFieldStart("fields");

      for (final VariableElement field : fields) {
        writeField(g, field);
      }

      g.writeEndArray();
    }
    List<ExecutableElement> methods = ElementFilter.methodsIn(members);
    {
      g.writeArrayFieldStart("methods");

      for (final ExecutableElement method : methods) {
        writeExecutable(g, method;
      }

      g.writeEndArray();
    }

    g.writeEndObject();
  }

  static void writeExecutable(JsonGenerator g, ExecutableElement exec, boolean ctor)
      throws IOException {
    g.writeStartObject();

    DocCommentTree doc = docTrees.getDocCommentTree(exec);
    g.writeObjectField("name", exec.getSimpleName());
    g.writeObjectField("comment_text", exec.getFullBody().stream().map(DocTree::toString).collect(Collectors.joining("")));

    // TODO: Write static or not
    if (!ctor) {
      TypeMirror returnType = exec.getReturnType();
      g.writeObjectFieldStart("returns");
      g.writeArrayFieldStart("types");
      g.writeString(returnType.toString());
      g.writeArrayEnd();
      g.writeObjectField("description", "DUMMY");
      g.writeEndObject();
    }

    {
      g.writeArrayFieldStart("parameters");

      Map<String, VariableElement> parameters = ctor.getParameters().stream().collect(Collectors.toMap(VariableElement::getSimpleName, x -> x));
      for (final VariableElement parameter : ctor.getParameters()) {
        // TODO: document parameters from javadoc PARAM tags
      }

      g.writeEndArray();
    }
    {
      g.writeArrayFieldStart("throws");

      for () {
        // TODO: Document throws from javadoc THROWS tags
      }

      g.writeEndArray();
    }

    g.writeEndObject();
  } 

  static void writeField(JsonGenerator g, FieldDoc doc)
      throws IOException {
    g.writeStartObject();

    g.writeObjectField("name", doc.name());
    g.writeObjectField("comment_text", doc.commentText());
    g.writeObjectField("type", doc.type().qualifiedTypeName());

    g.writeEndObject();
  }
}
