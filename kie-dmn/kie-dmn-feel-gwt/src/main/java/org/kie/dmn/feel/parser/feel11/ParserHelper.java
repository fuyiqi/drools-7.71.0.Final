/*
 * Copyright 2021 Red Hat, Inc. and/or its affiliates.
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

package org.kie.dmn.feel.parser.feel11;

import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.misc.Interval;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.kie.dmn.api.feel.runtime.events.FEELEvent;
import org.kie.dmn.feel.lang.CompositeType;
import org.kie.dmn.feel.lang.Scope;
import org.kie.dmn.feel.lang.SimpleType;
import org.kie.dmn.feel.lang.Symbol;
import org.kie.dmn.feel.lang.Type;
import org.kie.dmn.feel.lang.impl.FEELEventListenersManager;
import org.kie.dmn.feel.lang.types.AliasFEELType;
import org.kie.dmn.feel.lang.types.BuiltInType;
import org.kie.dmn.feel.lang.types.DefaultBuiltinFEELTypeRegistry;
import org.kie.dmn.feel.lang.types.FEELTypeRegistry;
import org.kie.dmn.feel.lang.types.GenListType;
import org.kie.dmn.feel.lang.types.ScopeImpl;
import org.kie.dmn.feel.lang.types.SymbolTable;
import org.kie.dmn.feel.lang.types.VariableSymbol;
import org.kie.dmn.feel.parser.feel11.FEEL_1_1Parser.FilterPathExpressionContext;
import org.kie.dmn.feel.parser.feel11.FEEL_1_1Parser.QualifiedNameContext;
import org.kie.dmn.feel.runtime.events.UnknownVariableErrorEvent;
import org.kie.dmn.feel.util.EvalHelper;

public class ParserHelper {

    private FEELEventListenersManager eventsManager;
    private SymbolTable symbols = new SymbolTable();
    private Scope currentScope = symbols.getGlobalScope();
    private Stack<String> currentName = new Stack<>();
    private int dynamicResolution = 0;
    private boolean featDMN12EnhancedForLoopEnabled = true; // DROOLS-2307 DMN enhanced for loop
    private boolean featDMN12weekday = true; // DROOLS-2648 DMN v1.2 weekday on 'date', 'date and time'
    private FEELTypeRegistry typeRegistry = DefaultBuiltinFEELTypeRegistry.INSTANCE;

    public ParserHelper() {
        this(null);
    }

    public ParserHelper(final FEELEventListenersManager eventsManager) {
        this.eventsManager = eventsManager;

        // Initial context is loaded
        currentName.push(Scope.LOCAL);
    }

    public SymbolTable getSymbolTable() {
        return symbols;
    }

    public void pushScope() {
        currentScope = new ScopeImpl(currentName.peek(), currentScope);
    }

    public void pushScope(final Type type) {
        currentScope = new ScopeImpl(currentName.peek(), currentScope, type);
    }

    public void setTypeRegistry(final FEELTypeRegistry typeRegistry) {
        this.typeRegistry = typeRegistry;
    }

    public void pushTypeScope() {
        currentScope = typeRegistry.getItemDefScope(currentScope);
    }

    public void popScope() {
        currentScope = currentScope.getParentScope();
    }

    public void pushName(final String name) {
        this.currentName.push(name);
    }

    public void pushName(final ParserRuleContext ctx) {
        this.currentName.push(getName(ctx));
    }

    private String getName(final ParserRuleContext ctx) {
        String key = getOriginalText(ctx);
        if (ctx instanceof FEEL_1_1Parser.KeyStringContext) {
            key = EvalHelper.unescapeString(key);
        }
        return key;
    }

    public void popName() {
        this.currentName.pop();
    }

    public void recoverScope() {
        recoverScope(currentName.peek());
    }

    public void recoverScope(final String name) {
        final Scope s = this.currentScope.getChildScopes().get(name);
        if (s != null) {
            currentScope = s;
            if (currentScope.getType() != null && currentScope.getType().equals(BuiltInType.UNKNOWN)) {
                enableDynamicResolution();
            }
        } else {
            final Symbol resolved = this.currentScope.resolve(name);
            Type scopeType = resolved != null ? resolved.getType() : null;
            if (scopeType instanceof GenListType) {
                scopeType = ((GenListType) scopeType).getGen();
            }

            if (resolved != null && scopeType instanceof CompositeType) {
                pushName(name);
                pushScope(scopeType);
                final CompositeType type = (CompositeType) scopeType;
                for (Map.Entry<String, Type> f : type.getFields().entrySet()) {
                    this.currentScope.define(new VariableSymbol(f.getKey(), f.getValue()));
                }
            } else if (resolved != null && scopeType instanceof SimpleType) {
                BuiltInType resolvedBIType = null;
                if (scopeType instanceof BuiltInType) {
                    resolvedBIType = (BuiltInType) scopeType;
                } else if (scopeType instanceof AliasFEELType) {
                    resolvedBIType = ((AliasFEELType) scopeType).getBuiltInType();
                } else {
                    throw new UnsupportedOperationException("Unsupported BIType " + scopeType + "!");
                }
                pushName(name);
                pushScope(resolvedBIType);
                switch (resolvedBIType) {
                    // FEEL spec table 53
                    case DATE:
                        this.currentScope.define(new VariableSymbol("year", BuiltInType.NUMBER));
                        this.currentScope.define(new VariableSymbol("month", BuiltInType.NUMBER));
                        this.currentScope.define(new VariableSymbol("day", BuiltInType.NUMBER));
                        if (isFeatDMN12weekday()) {
                            // Table 60 spec DMN v1.2
                            this.currentScope.define(new VariableSymbol("weekday", BuiltInType.NUMBER));
                        }
                        break;
                    case TIME:
                        this.currentScope.define(new VariableSymbol("hour", BuiltInType.NUMBER));
                        this.currentScope.define(new VariableSymbol("minute", BuiltInType.NUMBER));
                        this.currentScope.define(new VariableSymbol("second", BuiltInType.NUMBER));
                        this.currentScope.define(new VariableSymbol("time offset", BuiltInType.DURATION));
                        this.currentScope.define(new VariableSymbol("timezone", BuiltInType.NUMBER));
                        break;
                    case DATE_TIME:
                        this.currentScope.define(new VariableSymbol("year", BuiltInType.NUMBER));
                        this.currentScope.define(new VariableSymbol("month", BuiltInType.NUMBER));
                        this.currentScope.define(new VariableSymbol("day", BuiltInType.NUMBER));
                        if (isFeatDMN12weekday()) {
                            // Table 60 spec DMN v1.2
                            this.currentScope.define(new VariableSymbol("weekday", BuiltInType.NUMBER));
                        }
                        this.currentScope.define(new VariableSymbol("hour", BuiltInType.NUMBER));
                        this.currentScope.define(new VariableSymbol("minute", BuiltInType.NUMBER));
                        this.currentScope.define(new VariableSymbol("second", BuiltInType.NUMBER));
                        this.currentScope.define(new VariableSymbol("time offset", BuiltInType.DURATION));
                        this.currentScope.define(new VariableSymbol("timezone", BuiltInType.NUMBER));
                        break;
                    case DURATION:
                        this.currentScope.define(new VariableSymbol("years", BuiltInType.NUMBER));
                        this.currentScope.define(new VariableSymbol("months", BuiltInType.NUMBER));
                        this.currentScope.define(new VariableSymbol("days", BuiltInType.NUMBER));
                        this.currentScope.define(new VariableSymbol("hours", BuiltInType.NUMBER));
                        this.currentScope.define(new VariableSymbol("minutes", BuiltInType.NUMBER));
                        this.currentScope.define(new VariableSymbol("seconds", BuiltInType.NUMBER));
                        break;
                    case RANGE:
                        this.currentScope.define(new VariableSymbol("start included", BuiltInType.BOOLEAN));
                        this.currentScope.define(new VariableSymbol("start", BuiltInType.UNKNOWN));
                        this.currentScope.define(new VariableSymbol("end", BuiltInType.UNKNOWN));
                        this.currentScope.define(new VariableSymbol("end included", BuiltInType.BOOLEAN));
                        break;
                    // table 53 applies only to type(e) is a date/time/duration
                    case UNKNOWN:
                        enableDynamicResolution();
                        break;
                    default:
                        break;
                }
            } else {
                pushScope();
            }
        }
    }

    public void dismissScope() {
        if (currentScope.getType() != null && currentScope.getType().equals(BuiltInType.UNKNOWN)) {
            disableDynamicResolution();
        }
        popScope();
    }

    public void validateVariable(final ParserRuleContext ctx,
                                 final List<String> qn,
                                 final String name) {
        if (eventsManager != null && !isDynamicResolution()) {
            if (this.currentScope.getChildScopes().get(name) == null && this.currentScope.resolve(name) == null) {
                FEELEventListenersManager.notifyListeners(eventsManager, () -> {
                                                              final String varName = qn.stream().collect(Collectors.joining("."));
                                                              return new UnknownVariableErrorEvent(FEELEvent.Severity.ERROR,
                                                                                                   "Unknown variable '" + varName + "'",
                                                                                                   ctx.getStart().getLine(),
                                                                                                   ctx.getStart().getCharPositionInLine(),
                                                                                                   varName);
                                                          }
                );
            }
        }
    }

    public boolean isDynamicResolution() {
        return dynamicResolution > 0;
    }

    public void disableDynamicResolution() {
        if (dynamicResolution > 0) {
            this.dynamicResolution--;
        }
    }

    public void enableDynamicResolution() {
        this.dynamicResolution++;
    }

    public void defineVariable(final ParserRuleContext ctx) {
        defineVariable(getName(ctx));
    }

    public void defineVariable(final String variable) {
        this.currentScope.define(new VariableSymbol(variable));
    }

    public void defineVariable(final String variable,
                               final Type type) {
        this.currentScope.define(new VariableSymbol(variable, type));
    }

    public void startVariable(final Token t) {
        this.currentScope.start(t.getText());
    }

    public boolean followUp(final Token t,
                            final boolean isPredict) {
        boolean dynamicResolutionResult = isDynamicResolution() && FEELParser.isVariableNamePartValid(t.getText(), currentScope);
        boolean follow = dynamicResolutionResult || this.currentScope.followUp(t.getText(), isPredict);
        // in case isPredict == false, will need to followUp in the currentScope, so that the TokenTree currentNode is updated as per expectations,
        // this is because the `follow` variable above, in the case of short-circuited on `dynamicResolutionResult`,
        // would skip performing any necessary update in the second part of the || predicate
        if (dynamicResolutionResult && !isPredict) {
            this.currentScope.followUp(t.getText(), isPredict);
        }
        return follow;
    }

    public static String getOriginalText(final ParserRuleContext ctx) {
        int a = ctx.start.getStartIndex();
        int b = ctx.stop.getStopIndex();
        Interval interval = new Interval(a, b);
        return ctx.getStart().getInputStream().getText(interval);
    }
    
    /**
     * a specific heuristic for scope retrieval for filterPathExpression
     */
    public int fphStart(ParserRuleContext ctx, Parser parser) {
        if (!(ctx instanceof FEEL_1_1Parser.FilterPathExpressionContext)) { // I expect in `var[1].name` for this param ctx=`var[1]` to be a filterPathExpression
            return 0;
        }
        FilterPathExpressionContext ctx0 = (FEEL_1_1Parser.FilterPathExpressionContext) ctx;
        boolean ctxSquared = ctx0.filter != null && ctx0.n0 != null;
        if (!ctxSquared) { // I expect `var[1]` to be in the squared form `...[...]`
            return 0;
        }
        ParserRuleContext ctx1 = ctx0.n0.getRuleContext(FEEL_1_1Parser.NonSignedUnaryExpressionContext.class, 0);
        if (ctx1 == null) {
            return 0;
        }
        ParserRuleContext ctx2 = ctx1.getRuleContext(FEEL_1_1Parser.UenpmPrimaryContext.class, 0);
        if (ctx2 == null) {
            return 0;
        }
        ParserRuleContext ctx3 = ctx2.getRuleContext(FEEL_1_1Parser.PrimaryNameContext.class, 0);
        if (ctx3 == null) {
            return 0;
        }
        QualifiedNameContext ctx4 = ctx3.getRuleContext(FEEL_1_1Parser.QualifiedNameContext.class, 0);
        if (ctx4 == null) {
            return 0;
        } // I expect in this param ctx=`var[1]` for `var` to be a qualifiedName
        for (String n : ctx4.qns) {
            recoverScope(n);
        }
        return ctx4.qns.size();
    }
    
    public void fphEnd(int times) {
        for (int i = 0; i < times; i++) {
            dismissScope();
        }
    }

    public static List<Token> getAllTokens(final ParseTree ctx,
                                           final List<Token> tokens) {
        for (int i = 0; i < ctx.getChildCount(); i++) {
            final ParseTree child = ctx.getChild(i);
            if (child instanceof TerminalNode) {
                tokens.add(((TerminalNode) child).getSymbol());
            } else {
                getAllTokens(child, tokens);
            }
        }
        return tokens;
    }

    public boolean isFeatDMN12EnhancedForLoopEnabled() {
        return featDMN12EnhancedForLoopEnabled;
    }

    public void setFeatDMN12EnhancedForLoopEnabled(final boolean featDMN12EnhancedForLoopEnabled) {
        this.featDMN12EnhancedForLoopEnabled = featDMN12EnhancedForLoopEnabled;
    }

    public boolean isFeatDMN12weekday() {
        return featDMN12weekday;
    }

    public void setFeatDMN12weekday(final boolean featDMN12weekday) {
        this.featDMN12weekday = featDMN12weekday;
    }
}
