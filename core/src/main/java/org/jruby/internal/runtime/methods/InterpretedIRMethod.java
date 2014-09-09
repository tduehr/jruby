package org.jruby.internal.runtime.methods;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.jruby.Ruby;
import org.jruby.RubyInstanceConfig;
import org.jruby.RubyModule;
import org.jruby.ast.executable.Script;
import org.jruby.ir.*;
import org.jruby.ir.Compiler;
import org.jruby.ir.representations.CFG;
import org.jruby.ir.interpreter.Interpreter;
import org.jruby.ir.runtime.IRRuntimeHelpers;
import org.jruby.ir.targets.JVMVisitor;
import org.jruby.parser.StaticScope;
import org.jruby.runtime.Arity;
import org.jruby.runtime.Block;
import org.jruby.runtime.DynamicScope;
import org.jruby.runtime.Helpers;
import org.jruby.runtime.PositionAware;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.Visibility;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.ClassCache;
import org.jruby.util.cli.Options;
import org.jruby.util.log.Logger;
import org.jruby.util.log.LoggerFactory;

public class InterpretedIRMethod extends DynamicMethod implements IRMethodArgs, PositionAware {
    private static final Logger LOG = LoggerFactory.getLogger("InterpretedIRMethod");

    private Arity arity;
    private boolean displayedCFG = false; // FIXME: Remove when we find nicer way of logging CFG
    private boolean pushScope;

    protected final IRScope method;

    private static class DynamicMethodBox {
        public CompiledIRMethod actualMethod;
        public int callCount = 0;
    }

    private DynamicMethodBox box = new DynamicMethodBox();

    public InterpretedIRMethod(IRScope method, Visibility visibility, RubyModule implementationClass) {
        super(implementationClass, visibility, CallConfiguration.FrameNoneScopeNone);
        this.method = method;
        this.method.getStaticScope().determineModule();
        this.arity = calculateArity();
        this.pushScope = true;

        if (method.usesEval()) {
            // Methods that contain evals don't have interpreted parent context in JIT,
            // so we disable JIT here. FIXME: fix this some day?
            box.callCount = -1;
        }
    }

    // We can probably use IRMethod callArgs for something (at least arity)
    public InterpretedIRMethod(IRScope method, RubyModule implementationClass) {
        this(method, Visibility.PRIVATE, implementationClass);
    }

    public IRScope getIRMethod() {
        return method;
    }

    public CompiledIRMethod getCompiledIRMethod() {
        return box.actualMethod;
    }

    public List<String[]> getParameterList() {
        return (method instanceof IRMethod) ? ((IRMethod)method).getArgDesc() : new ArrayList<String[]>();
    }

    private Arity calculateArity() {
        StaticScope s = method.getStaticScope();
        if (s.getOptionalArgs() > 0 || s.getRestArg() >= 0) return Arity.required(s.getRequiredArgs());

        return Arity.createArity(s.getRequiredArgs());
    }

    @Override
    public Arity getArity() {
        return this.arity;
    }

    @Override
    public IRubyObject call(ThreadContext context, IRubyObject self, RubyModule clazz, String name, IRubyObject[] args, Block block) {
        if (tryCompile(context)) return callJitted(context, self, clazz, name, args, block);

        ensureInstrsReady();

        if (IRRuntimeHelpers.isDebug()) doDebug();

        if (method.hasExplicitCallProtocol()) return Interpreter.INTERPRET_METHOD(context, this, self, name, args, block);

        try {
            pre(context, self, name, block);

            return Interpreter.INTERPRET_METHOD(context, this, self, name, args, block);
        } finally {
            post(context);
        }
    }

    protected void doDebug() {
        // FIXME: name should probably not be "" ever.
        String realName = name == null || "".equals(name) ? method.getName() : name;
        LOG.info("Executing '" + realName + "'");
        if (displayedCFG == false) {
            // The base IR may not have been processed yet
            CFG cfg = method.getCFG();
            LOG.info("Graph:\n" + cfg.toStringGraph());
            LOG.info("CFG:\n" + cfg.toStringInstrs());
            displayedCFG = true;
        }
    }

    protected void post(ThreadContext context) {
        // update call stacks (pop: ..)
        context.popFrame();
        if (this.pushScope) {
            context.popScope();
        }
    }

    protected void pre(ThreadContext context, IRubyObject self, String name, Block block) {
        // update call stacks (push: frame, class, scope, etc.)
        StaticScope ss = method.getStaticScope();
        context.preMethodFrameAndClass(getImplementationClass().getMethodLocation(), name, self, block, ss);
        if (this.pushScope) {
            context.pushScope(DynamicScope.newDynamicScope(ss));
        }
        context.setCurrentVisibility(getVisibility());
    }

    private IRubyObject callJitted(ThreadContext context, IRubyObject self, RubyModule clazz, String name, IRubyObject[] args, Block block) {
        return box.actualMethod.call(context, self, clazz, name, args, block);
    }

    private void ensureInstrsReady() {
        // SSS FIXME: Move this out of here to some other place?
        // Prepare method if not yet done so we know if the method has an explicit/implicit call protocol
        if (method.getInstrsForInterpretation() == null) {
            method.prepareForInterpretation(false);
            this.pushScope = method.getFlags().contains(IRFlags.REQUIRES_DYNSCOPE) || !method.getFlags().contains(IRFlags.DYNSCOPE_ELIMINATED);
        }
    }

    private boolean tryCompile(ThreadContext context) {
        if (box.actualMethod != null) {
            return true;
        }

        if (box.callCount == -1) return false;

        if (box.callCount++ >= Options.JIT_THRESHOLD.load()) {

            box.callCount = -1; // disable...we get one shot
            Ruby runtime = context.runtime;
            RubyInstanceConfig config = runtime.getInstanceConfig();

            if (config.getCompileMode().shouldJIT()) {

                ensureInstrsReady();

                try {
                    Class compiled = JVMVisitor.compile(runtime, method, new ClassCache.OneShotClassLoader(context.runtime.getJRubyClassLoader()));
                    Method scriptMethod = compiled.getMethod("__script__", ThreadContext.class,
                            StaticScope.class, IRubyObject.class, IRubyObject[].class, Block.class);
                    MethodHandle handle = MethodHandles.publicLookup().unreflect(scriptMethod);
                    box.actualMethod = new CompiledIRMethod(handle, getName(), getFile(), getLine(), method.getStaticScope(), getVisibility(), getImplementationClass().getMethodLocation(), Helpers.encodeParameterList(getParameterList()), method.hasExplicitCallProtocol());

                    if (config.isJitLogging()) {
                        LOG.info("done jitting: " + method);
                    }

                    return true;
                } catch (Throwable e) {
                    if (config.isJitLogging()) {
                        LOG.info("failed to jit (" + e.getMessage() + "): " + method);
                        if (config.isJitLoggingVerbose()) {
                            StringWriter trace = new StringWriter();
                            PrintWriter writer = new PrintWriter(trace);
                            e.printStackTrace(writer);
                            LOG.info(trace.toString());
                        }
                    }
                }
            }
        }
        return false;
    }

    protected void dupBox(InterpretedIRMethod orig) {
        this.box = orig.box;
    }

    @Override
    public DynamicMethod dup() {
        InterpretedIRMethod x = new InterpretedIRMethod(method, visibility, implementationClass);
        x.box = box;

        return x;
    }

    public String getFile() {
        return method.getFileName();
    }

    public int getLine() {
        return method.getLineNumber();
   }
}
