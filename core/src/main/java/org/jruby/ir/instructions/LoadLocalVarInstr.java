package org.jruby.ir.instructions;

import org.jruby.ir.IRScope;
import org.jruby.ir.IRVisitor;
import org.jruby.ir.Interp;
import org.jruby.ir.Operation;
import org.jruby.ir.operands.*;
import org.jruby.ir.transformations.inlining.InlinerInfo;
import org.jruby.parser.StaticScope;
import org.jruby.runtime.DynamicScope;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;

public class LoadLocalVarInstr extends Instr implements ResultInstr, FixedArityInstr {
    private final IRScope scope;
    private TemporaryLocalVariable result;

    /** This is the variable that is being loaded from the scope.  This variable
     * doesn't participate in the computation itself.  We just use it as a proxy for
     * its (a) name (b) offset (c) scope-depth. */
    private LocalVariable lvar;

    public LoadLocalVarInstr(IRScope scope, TemporaryLocalVariable result, LocalVariable lvar) {
        super(Operation.BINDING_LOAD);

        assert result != null: "LoadLocalVarInstr result is null";

        this.lvar = lvar;
        this.result = result;
        this.scope = scope;
    }

    public IRScope getScope() {
        return scope;
    }

    @Override
    public Operand[] getOperands() {
        return new Operand[] { lvar };
    }

    @Override
    public Variable getResult() {
        return result;
    }

    @Override
    public void updateResult(Variable v) {
        this.result = (TemporaryLocalVariable)v;
    }

    // SSS FIXME: This feels dirty
    public void decrementLVarScopeDepth() {
        this.lvar = lvar.cloneForDepth(lvar.getScopeDepth()-1);
    }

    @Override
    public String toString() {
        return result + " = load_lvar(" + scope.getName() + ", " + lvar + ")";
    }

    @Override
    public Instr cloneForInlining(InlinerInfo ii) {
        // SSS FIXME: Do we need to rename lvar really?  It is just a name-proxy!
        return new LoadLocalVarInstr(scope, (TemporaryLocalVariable)ii.getRenamedVariable(result), (LocalVariable)ii.getRenamedVariable(lvar));
    }

    @Interp
    @Override
    public Object interpret(ThreadContext context, StaticScope currScope, DynamicScope currDynScope, IRubyObject self, Object[] temp) {
        return lvar.retrieve(context, self, currScope, currDynScope, temp);
    }

    @Override
    public void visit(IRVisitor visitor) {
        visitor.LoadLocalVarInstr(this);
    }

    public LocalVariable getLocalVar() {
        return lvar;
    }
}
