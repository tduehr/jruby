package org.jruby.ir.instructions;

import org.jruby.ir.IRVisitor;
import org.jruby.ir.Operation;
import org.jruby.ir.operands.Label;
import org.jruby.ir.operands.Operand;
import org.jruby.ir.transformations.inlining.InlinerInfo;
import org.jruby.parser.StaticScope;
import org.jruby.runtime.DynamicScope;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;

public class BNilInstr extends OneOperandBranchInstr  implements FixedArityInstr {
    public BNilInstr(Operand v, Label jmpTarget) {
        super(Operation.B_NIL, v, jmpTarget);
    }

    @Override
    public Instr cloneForInlining(InlinerInfo ii) {
        return new BNilInstr(getArg1().cloneForInlining(ii), ii.getRenamedLabel(getJumpTarget()));
    }

    @Override
    public void visit(IRVisitor visitor) {
        visitor.BNilInstr(this);
    }

    @Override
    public int interpretAndGetNewIPC(ThreadContext context, DynamicScope currDynScope, StaticScope currScope, IRubyObject self, Object[] temp, int ipc) {
        Object value1 = getArg1().retrieve(context, self, currScope, currDynScope, temp);
        return value1 == context.nil ? getJumpTarget().getTargetPC() : ipc;
    }
}
