/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.drools.mvel.integrationtests.phreak;

import java.util.HashSet;
import java.util.List;

import org.drools.core.addon.ClassTypeResolver;
import org.drools.core.base.ClassFieldAccessorCache;
import org.drools.core.base.ClassFieldAccessorStore;
import org.drools.core.base.ClassObjectType;
import org.drools.core.base.DroolsQuery;
import org.drools.core.base.ValueType;
import org.drools.core.base.evaluators.EvaluatorRegistry;
import org.drools.core.base.evaluators.Operator;
import org.drools.core.definitions.InternalKnowledgePackage;
import org.drools.core.definitions.impl.KnowledgePackageImpl;
import org.drools.core.rule.Declaration;
import org.drools.core.rule.Pattern;
import org.drools.core.spi.BetaNodeFieldConstraint;
import org.drools.core.spi.Evaluator;

public class ReteTesterHelper {

    private InternalKnowledgePackage pkg;
    private ClassFieldAccessorStore store;
    private EvaluatorRegistry registry = new EvaluatorRegistry();
    private final ClassTypeResolver typeResolver;

    public ReteTesterHelper() {
        this.pkg = new KnowledgePackageImpl("org.drools.examples.manners");
        this.pkg.setClassFieldAccessorCache(new ClassFieldAccessorCache(Thread.currentThread().getContextClassLoader()));
        this.store = this.pkg.getClassFieldAccessorStore();
        this.store.setEagerWire(true);
        this.typeResolver = new ClassTypeResolver(new HashSet<String>(),
                                                  getClass().getClassLoader());
    }

    public InternalKnowledgePackage getPkg() {
        return pkg;
    }

    public ClassFieldAccessorStore getStore() {
        return store;
    }

    public EvaluatorRegistry getRegistry() {
        return registry;
    }

    public ClassTypeResolver getTypeResolver() {
        return typeResolver;
    }

    public BetaNodeFieldConstraint getBoundVariableConstraint(final Class clazz,
                                                              final String fieldName,
                                                              final Declaration declaration,
                                                              final String evaluatorString) {
        return new FakeBetaNodeFieldConstraint(clazz, fieldName, declaration, evaluatorString);
    }

    public Evaluator getEvaluator(Class<?> cls,
                                  String operator) {
        if (cls == DroolsQuery.class) {
            cls = Object.class;
        }
        return registry.getEvaluator(ValueType.determineValueType(cls),
                                     Operator.determineOperator(operator,
                                                                false));
    }

    public Pattern getPattern(int index,
                              String type) throws ClassNotFoundException {
        return new Pattern(index, new ClassObjectType(typeResolver.resolveType(type)));
    }

    public void addImports(List<String> imports) {
        typeResolver.clearImports();
        for (String importEntry : imports) {
            typeResolver.addImport(importEntry);
        }
    }
}
