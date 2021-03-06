/*
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 1.0
 * GNU General Public License version 2
 * GNU Lesser General Public License version 2.1
 */
package org.jruby.truffle.nodes.control;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.source.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.api.utilities.*;
import org.jruby.truffle.nodes.*;
import org.jruby.truffle.nodes.cast.*;
import org.jruby.truffle.runtime.*;

/**
 * Represents a Ruby {@code if} expression. Note that in this representation we always have an
 * {@code else} part.
 */
public class IfNode extends RubyNode {

    @Child protected BooleanCastNode condition;
    @Child protected RubyNode thenBody;
    @Child protected RubyNode elseBody;

    private final BranchProfile thenProfile = new BranchProfile();
    private final BranchProfile elseProfile = new BranchProfile();

    @CompilerDirectives.CompilationFinal private int thenCount;
    @CompilerDirectives.CompilationFinal private int elseCount;

    public IfNode(RubyContext context, SourceSection sourceSection, BooleanCastNode condition, RubyNode thenBody, RubyNode elseBody) {
        super(context, sourceSection);
        this.condition = condition;
        this.thenBody = thenBody;
        this.elseBody = elseBody;
    }

    @Override
    public Object execute(VirtualFrame frame) {
        if (CompilerDirectives.injectBranchProbability(getBranchProbability(), condition.executeBoolean(frame))) {
            if (CompilerDirectives.inInterpreter()) {
                thenCount++;
            }
            thenProfile.enter();
            return thenBody.execute(frame);
        } else {
            if (CompilerDirectives.inInterpreter()) {
                elseCount++;
            }
            elseProfile.enter();
            return elseBody.execute(frame);
        }
    }

    private double getBranchProbability() {
        final int totalCount = thenCount + elseCount;

        if (totalCount == 0) {
            return 0;
        } else {
            return (double) thenCount / (double) (thenCount + elseCount);
        }
    }

}
