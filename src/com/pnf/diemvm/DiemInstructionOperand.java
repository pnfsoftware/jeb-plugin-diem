/*
 * JEB Copyright PNF Software, Inc.
 * 
 *     https://www.pnfsoftware.com
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pnf.diemvm;

import com.pnf.diemvm.Diem.OpndType;
import com.pnfsoftware.jeb.core.units.code.IInstruction;
import com.pnfsoftware.jeb.core.units.code.IInstructionOperand;
import com.pnfsoftware.jeb.util.serialization.annotations.Ser;
import com.pnfsoftware.jeb.util.serialization.annotations.SerId;

/**
 * Representation of a Diem instruction inline operand (non-stack).
 * <p>
 * Those are either branch targets, uint64 immediates, or indices into various object pools.
 *
 * @author Nicolas Falliere
 *
 */
@Ser
public class DiemInstructionOperand implements IInstructionOperand {
    @SerId(1)
    private OpndType type;
    @SerId(2)
    private Object object;

    public DiemInstructionOperand(OpndType type, Object object) {
        this.type = type;
        this.object = object;
    }

    public OpndType getOperandType() {
        return type;
    }

    public Object getObject() {
        return object;
    }

    @Override
    public String format(IInstruction insn, long address) {
        return String.format("%s(%s)", type, object);
    }

    @Override
    public String toString() {
        return format(null, 0);
    }
}