package org.jruby.ir.instructions;

import org.jruby.ir.IRFlags;
import org.jruby.ir.IRScope;
import org.jruby.ir.IRVisitor;
import org.jruby.ir.Operation;
import org.jruby.ir.operands.*;
import org.jruby.ir.runtime.IRRuntimeHelpers;
import org.jruby.ir.transformations.inlining.InlinerInfo;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;

public class ReceiveKeywordArgInstr extends ReceiveArgBase implements FixedArityInstr {
    public final String argName;
    public final int required;

    public ReceiveKeywordArgInstr(Variable result, String argName, int required) {
        super(Operation.RECV_KW_ARG, result, -1);
        this.argName = argName;
        this.required = required;
    }

    @Override
    public Operand[] getOperands() {
        return new Operand[] { new Fixnum(required), new StringLiteral(argName) };
    }

    @Override
    public String toString() {
        return (isDead() ? "[DEAD]" : "") + (hasUnusedResult() ? "[DEAD-RESULT]" : "") + getResult() + " = " + getOperation() + "(" + required + ", " + argName + ")";
    }

    @Override
    public boolean computeScopeFlags(IRScope scope) {
        scope.getFlags().add(IRFlags.RECEIVES_KEYWORD_ARGS);
        return true;
    }

    @Override
    public Instr cloneForInlining(InlinerInfo ii) {
        return new ReceiveKeywordArgInstr(ii.getRenamedVariable(result), argName, required);
    }

    @Override
    public IRubyObject receiveArg(ThreadContext context, IRubyObject[] args, boolean acceptsKeywordArgument) {
        return IRRuntimeHelpers.receiveKeywordArg(context, args, required, argName, acceptsKeywordArgument);
    }

    @Override
    public void visit(IRVisitor visitor) {
        visitor.ReceiveKeywordArgInstr(this);
    }
}
