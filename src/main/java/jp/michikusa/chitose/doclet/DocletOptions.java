package jp.michikusa.chitose.doclet;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import jdk.javadoc.doclet.Doclet.Option;

public class DocletOptions
{
    static class OutputFile implements Option {

        private File file;

        public File get() {
            return this.file;
        }

        @Override
        public int getArgumentCount() {
            return 1;
        }

        @Override
        public String getDescription() {
            return "File where JSON is written to";
        }

        @Override
        public Kind getKind() {
            return Kind.STANDARD;
        }

        @Override
        public List<String> getNames() {
            return Collections.singletonList("-ofile");
        }

        @Override
        public String getParameters() {
            return "file";
        }

        @Override
        public boolean process(String s, List<String> list) {
            this.file = new File(list.get(0));
            if (!this.file.exists()) {
                try {
                    return this.file.createNewFile();
                } catch (IOException ex) {
                    // it's ok, maybe something else made it?
                }
            }
            return this.file.exists();
        }
    }

    static class Append implements Option {

        private boolean value;

        public boolean get() {
            return this.value;
        }

        @Override
        public int getArgumentCount() {
            return 0;
        }

        @Override
        public String getDescription() {
            return "If set, appends to the file. (overwrites by default)";
        }

        @Override
        public Kind getKind() {
            return Kind.STANDARD;
        }

        @Override
        public List<String> getNames() {
            return Collections.singletonList("-append");
        }

        @Override
        public String getParameters() {
            return "";
        }

        @Override
        public boolean process(String s, List<String> list) {
            this.value = true;
            return true;
        }
    }

    static class Pretty implements Option {

        private boolean value;

        public boolean get() {
            return this.value;
        }

        @Override
        public int getArgumentCount() {
            return 0;
        }

        @Override
        public String getDescription() {
            return "If set, pretty prints the JSON output";
        }

        @Override
        public Kind getKind() {
            return Kind.STANDARD;
        }

        @Override
        public List<String> getNames() {
            return Collections.singletonList("-pretty");
        }

        @Override
        public String getParameters() {
            return "";
        }

        @Override
        public boolean process(String s, List<String> list) {
            this.value = true;
            return true;
        }
    }

    public static final OutputFile OUTPUT_FILE = new OutputFile();

    public static final Append APPEND = new Append();

    public static final Pretty PRETTY = new Pretty();


    public static OutputStream openOutputStream()
        throws IOException
    {
        return new FileOutputStream(OUTPUT_FILE.get(), APPEND.get());
    }

    public static boolean isPretty()
    {
        return PRETTY.get();
    }
}
