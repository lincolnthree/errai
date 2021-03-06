package org.jboss.errai.codegen.util;

import org.eclipse.jdt.core.compiler.CompilationProgress;
import org.eclipse.jdt.core.compiler.batch.BatchCompiler;
import org.jboss.errai.common.metadata.MetaDataScanner;
import org.jboss.errai.common.metadata.RebindUtils;
import org.slf4j.Logger;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * @author Mike Brock
 */
public class ClassChangeUtil {
  private static final String USE_NATIVE_JAVA_COMPILER = "errai.marshalling.use_native_javac";
  private static final String CLASSLOADING_MODE_PROPERTY = "errai.marshalling.classloading.mode";

  private static final String classLoadingMode;
  private static final boolean useNativeJavac = Boolean.getBoolean(USE_NATIVE_JAVA_COMPILER);
  private static Logger log = getLogger("ErraiMarshalling");

  static {
    if (System.getProperty(CLASSLOADING_MODE_PROPERTY) != null) {
      classLoadingMode = System.getProperty(CLASSLOADING_MODE_PROPERTY);
    }
    else {
      classLoadingMode = "thread";
    }
  }


  private static interface CompilerAdapter {
    int compile(OutputStream out, OutputStream errors, String outputPath, String toCompile, String classpath);
  }

  public static class JDKCompiler implements CompilerAdapter {
    final JavaCompiler compiler;

    public JDKCompiler(JavaCompiler compiler) {
      this.compiler = compiler;
    }

    @Override
    public int compile(OutputStream out, OutputStream errors, String outputPath, String toCompile, String classpath) {
      return compiler.run(null, out, errors, "-classpath", classpath, "-d", outputPath, toCompile);
    }
  }

  public static class JDTCompiler implements CompilerAdapter {
    @Override
    public int compile(OutputStream out, OutputStream errors, String outputPath, String toCompile, String classpath) {
      return BatchCompiler.compile("-classpath \"" + classpath + "\" -d " + outputPath + " -source 1.6 " + toCompile, new PrintWriter(out), new PrintWriter(errors),
              new CompilationProgress() {
                @Override
                public void begin(int remainingWork) {
                }

                @Override
                public void done() {
                }

                @Override
                public boolean isCanceled() {
                  return false;
                }

                @Override
                public void setTaskName(String name) {
                }

                @Override
                public void worked(int workIncrement, int remainingWork) {
                }
              }) ? 0 : -1;
    }
  }


  public static String compileClass(String sourcePath, String packageName, String className, String outputPath) {
    try {
      File inFile = new File(sourcePath + File.separator + className + ".java");
      //  File outFile = new File(sourcePath + File.separator + className + ".class");

      ByteArrayOutputStream errorOutputStream = new ByteArrayOutputStream();
      JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
      CompilerAdapter adapter;

      if (compiler == null || !useNativeJavac) {
        adapter = new JDTCompiler();
      }
      else {
        adapter = new JDKCompiler(compiler);
      }

      File classOutputDir = new File(outputPath
              + File.separatorChar + RebindUtils.packageNameToDirName(packageName)
              + File.separatorChar).getAbsoluteFile();

      // delete any marshaller classes already there
      Pattern matcher = Pattern.compile("^" + className + "(\\.|$).*class$");
      if (classOutputDir.exists()) {
        for (File file : classOutputDir.listFiles()) {
          if (matcher.matcher(file.getName()).matches()) {
            file.delete();
          }
        }
      }

      final StringBuilder sb = new StringBuilder(4096);

      List<URL> configUrls = MetaDataScanner.getConfigUrls();
      List<File> classpathElements = new ArrayList<File>(configUrls.size());

      log.debug(">>> Searching for all jars by " + MetaDataScanner.ERRAI_CONFIG_STUB_NAME);
      for (URL url : configUrls) {
        File file = getFileIfExists(url.getFile());
        if (file != null) {
          classpathElements.add(file);
        }
      }
      log.debug("<<< Done searching for all jars by " + MetaDataScanner.ERRAI_CONFIG_STUB_NAME);

      for (File file : classpathElements) {
        sb.append(file.getAbsolutePath()).append(File.pathSeparator);
      }

      sb.append(System.getProperty("java.class.path"));
      sb.append(findAllJarsByManifest());

      final String classPath = sb.toString();

      /**
       * Attempt to run the compiler without any classpath specified.
       */
      if (adapter.compile(System.out, errorOutputStream, outputPath, inFile.getAbsolutePath(), classPath) != 0) {

        System.out.println("*** FAILED TO COMPILE MARSHALLER CLASS ***");
        System.out.println("*** Classpath Used: " + sb.toString());

        for (byte b : errorOutputStream.toByteArray()) {
          System.out.print((char) b);
        }
        return null;
      }


      return new File(classOutputDir.getAbsolutePath() + File.separatorChar
              + className + ".class").getAbsolutePath();
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static Class loadClassDefinition(String path, String packageName, String className) throws IOException {
    if (path == null) return null;

    FileInputStream inputStream = new FileInputStream(path);
    byte[] classDefinition = new byte[inputStream.available()];

    String classBase = path.substring(0, path.length() - ".class".length());

    BootstrapClassloader clsLoader = new BootstrapClassloader(new File(path).getParentFile().getAbsolutePath(),
            "system".equals(classLoadingMode) ?
                    ClassLoader.getSystemClassLoader() :
                    Thread.currentThread().getContextClassLoader());

    try {
      return clsLoader.loadClass(packageName + "." + className);
    }
    catch (Throwable t) {
      // fall through
    }

    inputStream.read(classDefinition);

    for (File file : new File(path).getParentFile().listFiles()) {
      if (file.getName().startsWith(className + "$")) {
        String s = file.getName();
        s = s.substring(s.indexOf('$') + 1, s.lastIndexOf('.'));

        String fqcn = packageName + "." + className + "$" + s;

        Class cls = null;
        try {
          cls = clsLoader.loadClass(fqcn);
        }
        catch (ClassNotFoundException e) {
        }

        if (cls != null) continue;

        String innerClassBaseName = classBase + "$" + s;
        File innerClass = new File(innerClassBaseName + ".class");
        if (innerClass.exists()) {
          try {
            inputStream = new FileInputStream(innerClass);
            classDefinition = new byte[inputStream.available()];
            inputStream.read(classDefinition);

            clsLoader.defineClassX(fqcn, classDefinition, 0, classDefinition.length);
          }
          finally {
            inputStream.close();
          }
        }
        else {
          break;
        }
      }
    }

    Class<?> mainClass = clsLoader
            .defineClassX(packageName + "." + className, classDefinition, 0, classDefinition.length);

    inputStream.close();

    for (int i = 1; i < Integer.MAX_VALUE; i++) {

      String fqcn = packageName + "." + className + "$" + i;

      Class cls = null;
      try {
        cls = clsLoader.loadClass(fqcn);
      }
      catch (ClassNotFoundException e) {
      }

      if (cls != null) continue;


      String innerClassBaseName = classBase + "$" + i;
      File innerClass = new File(innerClassBaseName + ".class");
      if (innerClass.exists()) {
        try {
          inputStream = new FileInputStream(innerClass);
          classDefinition = new byte[inputStream.available()];
          inputStream.read(classDefinition);

          clsLoader.defineClassX(fqcn, classDefinition, 0, classDefinition.length);
        }
        finally {
          inputStream.close();
        }
      }
      else {
        break;
      }
    }

    return mainClass;
  }

  private static class BootstrapClassloader extends ClassLoader {
    private String searchPath;

    private BootstrapClassloader(String searchPath, ClassLoader classLoader) {
      super(classLoader);
      this.searchPath = searchPath;
    }

    public Class<?> defineClassX(String className, byte[] b, int off, int len) {
      return super.defineClass(className, b, off, len);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
      try {
        return super.findClass(name);
      }
      catch (ClassNotFoundException e) {
        try {
          FileInputStream inputStream = null;
          byte[] classDefinition;


          File innerClass = new File(searchPath + "/" + name.substring(name.lastIndexOf('.') + 1) + ".class");
          if (innerClass.exists()) {
            try {
              inputStream = new FileInputStream(innerClass);
              classDefinition = new byte[inputStream.available()];
              inputStream.read(classDefinition);

              return defineClassX(name, classDefinition, 0, classDefinition.length);
            }
            finally {
              if (inputStream != null) inputStream.close();
            }

          }
        }
        catch (IOException e2) {
          throw new RuntimeException("failed to load class: " + name, e2);
        }


      }
      throw new ClassNotFoundException(name);
    }
  }

  private static String findAllJarsByManifest() {
    StringBuilder cp = new StringBuilder();
    try {
      log.debug(">>> Searching for all jars by " + JarFile.MANIFEST_NAME);
      Enumeration[] enumers = new Enumeration[]
              {
                      Thread.currentThread().getContextClassLoader().getResources(JarFile.MANIFEST_NAME),
                      ClassLoader.getSystemClassLoader().getResources(JarFile.MANIFEST_NAME)
              };

      for (Enumeration resEnum : enumers) {
        while (resEnum.hasMoreElements()) {
          InputStream is = null;
          try {
            URL url = (URL) resEnum.nextElement();

            File file = getFileIfExists(url.getFile());
            if (file != null) {
              cp.append(File.pathSeparator).append(file.getAbsolutePath());
            }
          }
          catch (Exception e) {
            log.warn("ignoring classpath entry with invalid manifest", e);
          }
          finally {
            if (is != null) is.close();
          }
        }
      }
    }
    catch (IOException e1) {
      // Silently ignore wrong manifests on classpath?
      log.warn("failed to build classpath using manifest discovery. Expect compile failures...", e1);
    }
    finally {
      log.debug("<<< Done searching for all jars by " + JarFile.MANIFEST_NAME);
    }

    return cp.toString();
  }

  public static File getFileIfExists(String path) {
    final String originalPath = path;

    if (path.startsWith("file:")) {
      path = path.substring(5);

      int outerElement = path.indexOf('!');
      if (outerElement != -1) {
        path = path.substring(0, outerElement);
      }
    }
    else if (path.startsWith("jar:")) {
      path = path.substring(4);

      int outerElement = path.indexOf('!');
      if (outerElement != -1) {
        path = path.substring(0, outerElement);
      }
    }

    File file = new File(path);
    if (file.exists()) {
      if (log.isDebugEnabled()) {
        log.debug("   EXISTS: " + originalPath + " -> " + file.getAbsolutePath());
      }
      return file;
    }

    if (log.isDebugEnabled()) {
      log.debug("  !EXISTS: " + originalPath + " -> " + file.getAbsolutePath());
    }
    return null;
  }

  public static Set<File> findAllMatching(String fileName, File from) {
    HashSet<File> matching = new HashSet<File>();
    _findAllMatching(matching, fileName, from);
    return matching;
  }

  public static void _findAllMatching(HashSet<File> matching, String fileName, File from) {
    if (from.isDirectory()) {
      for (File file : from.listFiles()) {
        _findAllMatching(matching, fileName, file);
      }
    }
    else {
      if (fileName.equals(from.getName())) {
        matching.add(from);
      }
    }
  }

  public static Set<File> findMatchingOutputDirectoryByModel(Map<String, String> toMatch, File from) {
    HashSet<File> matching = new HashSet<File>();
    _findMatchingOutputDirectoryByModel(matching, toMatch, from);
    return matching;
  }

  private static void _findMatchingOutputDirectoryByModel(Set<File> matching, Map<String, String> toMatch, File from) {
    if (from.isDirectory()) {
      for (File file : from.listFiles()) {
        int currMatch = matching.size();
        _findMatchingOutputDirectoryByModel(matching, toMatch, file);
        if (matching.size() > currMatch) {
          break;
        }
      }
    }
    else {
      String name = from.getName();
      if (name.endsWith(".class") && toMatch.containsKey(name = name.substring(0, name.length() - 6))) {
        String full = toMatch.get(name);
        ReverseMatchResult res = reversePathMatch(full, from);

        if (res.isMatch()) {
          matching.add(res.getMatchRoot());
        }
      }
    }
  }


  private static ReverseMatchResult reversePathMatch(String fqcn, File location) {
    List<String> stk = new ArrayList<String>(Arrays.asList(fqcn.split("\\.")));

    File curr = location;

    if (!stk.isEmpty()) {
      // remove the last element -- as that would be the file name.
      stk.remove(stk.size() - 1);
    }

    while (!stk.isEmpty()) {
      String el = stk.remove(stk.size() - 1);
      curr = curr.getParentFile();
      if (curr == null || !curr.getName().equals(el)) {
        break;
      }
    }

    if (curr != null) {
      curr = curr.getParentFile();
    }

    if (stk.isEmpty()) {
      return new ReverseMatchResult(true, curr);
    }
    else {
      return new ReverseMatchResult(false, curr);
    }
  }

  private static class ReverseMatchResult {
    private final boolean match;
    private final File matchRoot;

    private ReverseMatchResult(boolean match, File matchRoot) {
      this.match = match;
      this.matchRoot = matchRoot;
    }

    public boolean isMatch() {
      return match;
    }

    public File getMatchRoot() {
      return matchRoot;
    }
  }

}
