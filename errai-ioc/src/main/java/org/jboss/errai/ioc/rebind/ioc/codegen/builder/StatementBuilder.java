package org.jboss.errai.ioc.rebind.ioc.codegen.builder;

import org.jboss.errai.ioc.rebind.ioc.codegen.HasScope;
import org.jboss.errai.ioc.rebind.ioc.codegen.Scope;
import org.jboss.errai.ioc.rebind.ioc.codegen.Variable;
import org.jboss.errai.ioc.rebind.ioc.codegen.builder.LoopBuilder.LoopBodyBuilder;
import org.jboss.errai.ioc.rebind.ioc.codegen.meta.MetaClass;
import org.jboss.errai.ioc.rebind.ioc.codegen.meta.impl.GWTClass;
import org.jboss.errai.ioc.rebind.ioc.codegen.meta.impl.JavaReflectionClass;

/**
 * 
 * @author Christian Sadilek <csadilek@redhat.com>
 */
public class StatementBuilder extends AbstractStatementBuilder {

    private StatementBuilder(Scope scope) {
        super(scope);
    }

    public static StatementBuilder create() {
        return new StatementBuilder(new Scope());
    }

    public static StatementBuilder createInScopeOf(HasScope parent) {
        return new StatementBuilder(parent.getScope());
    }

    public StatementBuilder loadVariable(String name, MetaClass type) {
        scope.pushVariable(new Variable(name, type));
        return this;
    }

    public ObjectBuilder newObject(GWTClass type) {
        return ObjectBuilder.newInstanceOf(type);
    }

    public ObjectBuilder newObject(JavaReflectionClass type) {
        return ObjectBuilder.newInstanceOf(type);
    }

    public LoopBodyBuilder foreach(String loopVarName) {
        return LoopBuilder.createInScopeOf(this).foreach(loopVarName);
    }
    
    public LoopBodyBuilder foreach(String loopVarName, String sequenceVarName) {
        return LoopBuilder.createInScopeOf(this).foreach(loopVarName, sequenceVarName);
    }
}
