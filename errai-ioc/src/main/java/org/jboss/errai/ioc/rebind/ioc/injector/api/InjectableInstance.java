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

package org.jboss.errai.ioc.rebind.ioc.injector.api;


import static org.jboss.errai.codegen.util.PrivateAccessUtil.getPrivateFieldInjectorName;
import static org.jboss.errai.codegen.util.PrivateAccessUtil.getPrivateMethodName;

import java.lang.annotation.Annotation;

import org.jboss.errai.codegen.Statement;
import org.jboss.errai.codegen.literal.LiteralFactory;
import org.jboss.errai.codegen.meta.MetaClass;
import org.jboss.errai.codegen.meta.MetaConstructor;
import org.jboss.errai.codegen.meta.MetaField;
import org.jboss.errai.codegen.meta.MetaMethod;
import org.jboss.errai.codegen.meta.MetaParameter;
import org.jboss.errai.codegen.util.Refs;
import org.jboss.errai.codegen.util.Stmt;
import org.jboss.errai.ioc.rebind.ioc.injector.InjectUtil;
import org.jboss.errai.ioc.rebind.ioc.injector.Injector;

public class InjectableInstance<T extends Annotation> extends InjectionPoint<T> {

  public InjectableInstance(T annotation, TaskType taskType, MetaConstructor constructor, MetaMethod method,
                            MetaField field, MetaClass type, MetaParameter parm, Injector injector, InjectionContext injectionContext) {

    super(annotation, taskType, constructor, method, field, type, parm, injector, injectionContext);
  }

  public static <T extends Annotation> InjectableInstance<T> getInjectedInstance(T annotation,
                                                                                 MetaClass type,
                                                                                 Injector injector,
                                                                                 InjectionContext context) {
    return new InjectableInstance<T>(annotation, TaskType.Type, null, null, null, type,
            null, injector, context);

  }

  public static <T extends Annotation> InjectableInstance<T> getMethodInjectedInstance(MetaMethod method,
                                                                                       Injector injector,
                                                                                       InjectionContext context) {

    //noinspection unchecked
    return new InjectableInstance(
            context.getMatchingAnnotationForElementType(WiringElementType.InjectionPoint, method),
            !method.isPublic() ? TaskType.PrivateMethod : TaskType.Method, null,
            method, null,
            method.getDeclaringClass(),
            null, injector, context);

  }

  public static <T extends Annotation> InjectableInstance<T> getParameterInjectedInstance(MetaParameter parm,
                                                                                          Injector injector,
                                                                                          InjectionContext context) {

    if (parm.getDeclaringMember() instanceof MetaConstructor) {

      //noinspection unchecked
      return new InjectableInstance(context.getMatchingAnnotationForElementType(WiringElementType.InjectionPoint,
              parm.getDeclaringMember()),
              TaskType.Parameter, ((MetaConstructor) parm.getDeclaringMember()),
              null, null, parm.getDeclaringMember().getDeclaringClass(), parm, injector, context);
    }
    else {
      //noinspection unchecked
      return new InjectableInstance(context.getMatchingAnnotationForElementType(WiringElementType.InjectionPoint,
              parm.getDeclaringMember()),
              TaskType.Parameter, null,
              ((MetaMethod) parm.getDeclaringMember()), null, parm.getDeclaringMember().getDeclaringClass(),
              parm, injector, context);
    }


  }


  public static <T extends Annotation> InjectableInstance<T> getFieldInjectedInstance(MetaField field,
                                                                                      Injector injector,
                                                                                      InjectionContext context) {

    //noinspection unchecked
    return new InjectableInstance(context.getMatchingAnnotationForElementType(WiringElementType.InjectionPoint,
            field),
            !field.isPublic() ? TaskType.PrivateField : TaskType.Field, null,
            null, field,
            field.getDeclaringClass(),
            null, injector, context);

  }

  /**
   * Returns an instance of a {@link Statement} which represents the value associated for injection at this
   * InjectionPoint.
   *
   * @return
   */
  public Statement getValueStatement() {

    Statement[] stmt;
    switch (taskType) {
      case PrivateField:
        if (field.isStatic()) {
          return Stmt.invokeStatic(injectionContext.getProcessingContext().getBootstrapClass(),
                  getPrivateFieldInjectorName(field));
        }
        else {
          return Stmt.invokeStatic(injectionContext.getProcessingContext().getBootstrapClass(),
                  getPrivateFieldInjectorName(field), Refs.get(getTargetInjector().getVarName()));
        }
      case Field:
        if (field.isStatic()) {
          return Stmt.loadStatic(getEnclosingType(), field.getName());
        }
        else {
          return Stmt.loadVariable(getTargetInjector().getVarName()).loadField(field.getName());
        }

      case PrivateMethod:
        if (method.getReturnType().isVoid()) {
          return Stmt.load(Void.class);
        }

        MetaParameter[] methParms = method.getParameters();
        Statement[] resolveParmsDeps = InjectUtil.resolveInjectionDependencies(methParms, injectionContext, method);

        if (method.isStatic()) {
          stmt = new Statement[methParms.length];
          System.arraycopy(resolveParmsDeps, 0, stmt, 0, methParms.length);
        }
        else {
          stmt = new Statement[methParms.length + 1];
          stmt[0] = Refs.get(getTargetInjector().getVarName());
          System.arraycopy(resolveParmsDeps, 0, stmt, 1, methParms.length);
        }

        //todo: this
        return Stmt.invokeStatic(injectionContext.getProcessingContext().getBootstrapClass(),
                getPrivateMethodName(method), stmt);

      case Method:
        stmt = InjectUtil.resolveInjectionDependencies(method.getParameters(), injectionContext, method);

        if (method.isStatic()) {
          return Stmt.invokeStatic(getEnclosingType(), method.getName(), stmt);
        }

        else {
          return Stmt.loadVariable(getTargetInjector().getVarName()).invoke(method, stmt);
        }

      case Parameter:
      case Type:
        return Refs.get(getTargetInjector().getVarName());

      default:
        return LiteralFactory.getLiteral(null);
    }
  }

  private Injector getTargetInjector() {
    Injector targetInjector
            = isProxy() ? injectionContext.getProxiedInjector(getEnclosingType(), getQualifyingMetadata()) :
            injectionContext.getQualifiedInjector(getEnclosingType(), getQualifyingMetadata());

    if (!isProxy()) {
      if (!targetInjector.isCreated()) {
        targetInjector = InjectUtil.getOrCreateProxy(injectionContext, getEnclosingType(), getQualifyingMetadata());
        targetInjector.getBeanInstance(this);
      }
    }

    return targetInjector;
  }

  public Statement callOrBind(Statement... values) {
    final Injector targetInjector = injector;

    MetaMethod meth = method;
    switch (taskType) {
      case PrivateField:
        Statement[] args = new Statement[values.length + 1];
        args[0] = Refs.get(targetInjector.getVarName());
        System.arraycopy(values, 0, args, 1, values.length);

        return Stmt.invokeStatic(injectionContext.getProcessingContext().getBootstrapClass(),
                getPrivateFieldInjectorName(field), args);

      case Field:
        return Stmt.loadVariable(targetInjector.getVarName()).loadField(field.getName()).assignValue(values[0]);

      case Parameter:
        if (parm.getDeclaringMember() instanceof MetaMethod) {
          meth = (MetaMethod) parm.getDeclaringMember();
        }
        else {
          throw new RuntimeException("cannot call task on element: " + parm.getDeclaringMember());
        }

      case Method:
      case PrivateMethod:
        args = new Statement[values.length + 1];
        args[0] = Refs.get(targetInjector.getVarName());
        System.arraycopy(values, 0, args, 1, values.length);

        if (!meth.isPublic()) {
          return Stmt.invokeStatic(injectionContext.getProcessingContext().getBootstrapClass(),
                  getPrivateMethodName(meth), args);
        }
        else {
          return Stmt.loadVariable(targetInjector.getVarName()).invoke(meth, values);
        }

      default:
        throw new RuntimeException("cannot call tasktype: " + taskType);
    }
  }
}
