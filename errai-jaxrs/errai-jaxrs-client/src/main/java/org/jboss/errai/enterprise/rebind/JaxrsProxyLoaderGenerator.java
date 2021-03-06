/*
 * Copyright 2011 JBoss, by Red Hat, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.errai.enterprise.rebind;

import java.io.File;
import java.io.PrintWriter;

import javax.ws.rs.Path;

import org.jboss.errai.bus.client.framework.ProxyProvider;
import org.jboss.errai.bus.client.framework.RemoteServiceProxyFactory;
import org.jboss.errai.codegen.InnerClass;
import org.jboss.errai.codegen.Statement;
import org.jboss.errai.codegen.builder.ClassStructureBuilder;
import org.jboss.errai.codegen.builder.MethodBlockBuilder;
import org.jboss.errai.codegen.builder.impl.ClassBuilder;
import org.jboss.errai.codegen.builder.impl.ObjectBuilder;
import org.jboss.errai.codegen.meta.impl.build.BuildMetaClass;
import org.jboss.errai.codegen.util.Stmt;
import org.jboss.errai.common.metadata.MetaDataScanner;
import org.jboss.errai.common.metadata.RebindUtils;
import org.jboss.errai.common.metadata.ScannerSingleton;
import org.jboss.errai.enterprise.client.jaxrs.JaxrsProxyLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gwt.core.ext.Generator;
import com.google.gwt.core.ext.GeneratorContext;
import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.UnableToCompleteException;
import com.google.gwt.core.ext.typeinfo.JClassType;

/**
 * Generates the JAX-RS proxy loader.
 * 
 * @author Christian Sadilek <csadilek@redhat.com>
 */
public class JaxrsProxyLoaderGenerator extends Generator {
  private Logger log = LoggerFactory.getLogger(JaxrsProxyLoaderGenerator.class);
  
  @Override
  public String generate(TreeLogger logger, GeneratorContext context, String typeName)
      throws UnableToCompleteException {
    String packageName = null;
    String className = null;
    
    try {
      JClassType classType = context.getTypeOracle().getType(typeName);

      if (classType.isInterface() == null) {
        logger.log(TreeLogger.ERROR, typeName + "is not an interface.");
        throw new RuntimeException("invalid type: not an interface");
      }

      packageName = classType.getPackage().getName();
      className = classType.getSimpleSourceName() + "Impl";

      PrintWriter printWriter = context.tryCreate(logger, packageName, className);
      // If code has not already been generated.
      if (printWriter != null) {
        printWriter.append(generate(context, logger, className));
        context.commit(logger, printWriter);
      }
    }
    catch (Throwable e) {
      logger.log(TreeLogger.ERROR, "Error generating JAX-RS extensions", e);
    }

    return packageName + "." + className;
  }
  
  private String generate(final GeneratorContext context, final TreeLogger logger, final String className) {
    File fileCacheDir = RebindUtils.getErraiCacheDir();
    File cacheFile = new File(fileCacheDir.getAbsolutePath() + "/" + className + ".java");
    
    String gen;
    if (RebindUtils.hasClasspathChangedForAnnotatedWith(Path.class) || !cacheFile.exists()) {
      log.info("generating jax-rs proxy loader class.");
      gen = generate(context, logger);
      RebindUtils.writeStringToFile(cacheFile, gen);
    } 
    else {
      log.info("nothing has changed. using cached jax-rs proxy loader class.");
      gen = RebindUtils.readFileToString(cacheFile);
    }
    
    return gen;
  }
  
  private String generate(final GeneratorContext context, final TreeLogger logger) {
    MetaDataScanner scanner = ScannerSingleton.getOrCreateInstance();
    ClassStructureBuilder<?> classBuilder = ClassBuilder.implement(JaxrsProxyLoader.class);

    MethodBlockBuilder<?> loadProxies = classBuilder.publicMethod(void.class, "loadProxies");
    for (Class<?> remote : scanner.getTypesAnnotatedWith(Path.class, RebindUtils.findTranslatablePackages(context))) {
      if (remote.isInterface()) {
        // create the remote proxy for this interface
        ClassStructureBuilder<?> remoteProxy = new JaxrsProxyGenerator(remote).generate();
        loadProxies.append(new InnerClass((BuildMetaClass) remoteProxy.getClassDefinition()));

        // create the proxy provider
        Statement proxyProvider = ObjectBuilder.newInstanceOf(ProxyProvider.class)
          .extend()
          .publicOverridesMethod("getProxy")
          .append(Stmt.nestedCall(Stmt.newObject(remoteProxy.getClassDefinition())).returnValue())
          .finish()
          .finish();
        
        loadProxies.append(Stmt.invokeStatic(RemoteServiceProxyFactory.class, "addRemoteProxy", remote, proxyProvider));
      }
    }
    classBuilder = (ClassStructureBuilder<?>) loadProxies.finish();
    
    String out = classBuilder.toJavaString();

    if (Boolean.getBoolean("errai.codegen.printOut")) {
      System.out.println("---JAX-RS Proxy-->");
      System.out.println(out);
      System.out.println("<--JAX-RS Proxy---");
    }
    return out;
  }
}