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
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.*;
import java.util.function.*;
import javax.lang.model.element.Element;
import static javax.lang.model.type.TypeKind.*;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
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

  private void write(JsonGenerator g, DocletEnvironment docEnv)
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

  private static boolean hasElement(TypeMirror mirror) {
    switch (mirror.getKind()) {
      case DECLARED:
      case ERROR:
      case TYPEVAR:
        return true;
      default:
        return false;
    }
  }

  private static Element getElement(TypeMirror mirror) {
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

  interface UnsafeConsumer<T, E extends Throwable> {
    void accept(T t) throws E;
  }

  private static <T, E extends Throwable> Consumer<T> unsafe(UnsafeConsumer<T, E> unsafe) {
    return t -> {
      try {
        unsafe.accept(t);
      } catch (Throwable e) {
        throw new RuntimeException(e);
      }
    };
  }

  private static String toString(List<? extends DocTree> tree) {
    return tree.stream().map(DocTree::toString).collect(Collectors.joining());
  }

  private static <T extends U, U extends DocTree> Stream<? extends T> filter(Collection<? extends U> iterable, Class<T> to, DocTree.Kind kind) {
    return iterable.stream().filter(tree -> tree.getKind() == kind).map(to::cast);
  }

  private void writeClass(JsonGenerator g, TypeElement type)
      throws IOException {
    g.writeStartObject();

    g.writeObjectField("name", type.getQualifiedName().toString());
    {
      g.writeArrayFieldStart("interfaces");

      type.getInterfaces().stream()
        .filter(JsonDoclet::hasElement)
        .map(JsonDoclet::getElement)
        .filter(TypeElement.class::isInstance)
        .map(TypeElement.class::cast)
        .forEach(unsafe(iface -> g.writeString(iface.getQualifiedName().toString()))); 

      g.writeEndArray();
    }
    TypeMirror superType = type.getSuperclass();
    g.writeObjectField("superclass",
        (superType != null && hasElement(superType) ? ((TypeElement) getElement(superType)).getQualifiedName().toString() : ""));
    
    System.out.println("Name was " + type.getQualifiedName());
    DocCommentTree docs = docTrees.getDocCommentTree(type);
    if (docs == null) return;
    String summary = toString(docs.getFullBody());
    g.writeObjectField("description", summary);
    filter(docs.getBlockTags(), SinceTree.class, DocTree.Kind.SINCE).findFirst()
      .ifPresent(unsafe(since -> {
        String value = toString(since.getBody());
        g.writeObjectField("since", value);
      }));

    Stream<? extends SeeTree> seeTags = filter(docs.getBlockTags(), SeeTree.class, DocTree.Kind.SEE);
    g.writeArrayFieldStart("see");
    seeTags.map(seeTag -> toString(seeTag.getReference()))
        .forEach(unsafe(g::writeString));

    List<? extends Element> members = type.getEnclosedElements();
    List<ExecutableElement> constructors = ElementFilter.constructorsIn(members);
    {
      g.writeArrayFieldStart("constructors");

      for (final ExecutableElement ctor : constructors) {
        writeExecutable(g, ctor, true);
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
        writeExecutable(g, method, false);
      }

      g.writeEndArray();
    }

    g.writeEndObject();
  }

  private void writeExecutable(JsonGenerator g, ExecutableElement exec, boolean ctor)
      throws IOException {
    g.writeStartObject();

    DocCommentTree docs = docTrees.getDocCommentTree(exec);
    g.writeObjectField("name", exec.getSimpleName());
    g.writeObjectField("description", toString(docs.getFullBody()));

    // TODO: Write static or not
    if (!ctor) {
      TypeMirror returnType = exec.getReturnType();
      g.writeObjectFieldStart("returns");
      g.writeArrayFieldStart("types");
      g.writeString(returnType.toString());
      g.writeEndArray();
      g.writeObjectField("description", "DUMMY");
      g.writeEndObject();
    }

    {
      g.writeArrayFieldStart("parameters");

      Map<String, ParamTree> parameters =
          filter(docs.getBlockTags(), ParamTree.class, DocTree.Kind.PARAM)
              .collect(
                  Collectors.toMap(tree -> tree.getName().getName().toString(),
                                   Function.identity()));
      for (final VariableElement parameter : exec.getParameters()) {
        String paramName = parameter.getSimpleName().toString();
        ParamTree doc = parameters.get(paramName);

        g.writeStartObject();
        g.writeObjectField("name", paramName);
        g.writeArrayFieldStart("types");
        g.writeString(((TypeElement) getElement(parameter.asType())).getQualifiedName().toString());
        g.writeEndArray();
        if (doc != null) {
          g.writeObjectField("description", toString(doc.getDescription()));
        }
        g.writeEndObject();
      }

      g.writeEndArray();
    }
    {
      g.writeArrayFieldStart("throws");
      filter(docs.getBlockTags(), ThrowsTree.class, DocTree.Kind.THROWS)
          .forEach(unsafe(thrown -> {
            g.writeStartObject();
            g.writeObjectField("type", thrown.getExceptionName().getSignature());
            g.writeObjectField("description", toString(thrown.getDescription()));
            g.writeEndObject();
          }));
      g.writeEndArray();
    }

    g.writeEndObject();
  } 

  private void writeField(JsonGenerator g, VariableElement field) throws IOException {
    g.writeStartObject();

    DocCommentTree docs = this.docTrees.getDocCommentTree(field);
    g.writeObjectField("name", field.getSimpleName().toString());
    g.writeObjectField("description", toString((docs.getFullBody())));
    g.writeObjectField("type", ((TypeElement) getElement(field.asType())).getQualifiedName().toString());

    g.writeEndObject();
  }
}
