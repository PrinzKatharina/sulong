/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package uk.ac.man.cs.llvm.ir.model.elements;

import java.util.ArrayList;
import java.util.List;

import uk.ac.man.cs.llvm.ir.model.InstructionVisitor;
import uk.ac.man.cs.llvm.ir.model.Symbol;
import uk.ac.man.cs.llvm.ir.model.ValueSymbol;
import uk.ac.man.cs.llvm.ir.types.Type;

public final class GetElementPointerInstruction extends ValueInstruction {

    private Symbol base;

    private final List<Symbol> indices = new ArrayList<>();

    private final boolean isInbounds;

    public GetElementPointerInstruction(Type type, boolean isInbounds) {
        super(type);
        this.isInbounds = isInbounds;
    }

    @Override
    public void accept(InstructionVisitor visitor) {
        visitor.visit(this);
    }

    public void addIndex(Symbol index) {
        indices.add(index);
    }

    @Override
    public int getAlign() {
        return ((ValueSymbol) base).getAlign();
    }

    public Symbol getIndex(int index) {
        return indices.get(index);
    }

    public int getIndexCount() {
        return indices.size();
    }

    public Symbol getBasePointer() {
        return base;
    }

    public boolean isInbounds() {
        return isInbounds;
    }

    @Override
    public void replace(Symbol original, Symbol replacement) {
        if (base == original) {
            base = replacement;
        }
        for (int i = 0; i < indices.size(); i++) {
            if (indices.get(i) == original) {
                indices.set(i, replacement);
            }
        }
    }

    public void setBasePointer(Symbol base) {
        this.base = base;
    }
}
