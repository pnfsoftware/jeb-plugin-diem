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

import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.pnf.diemvm.Diem.OpcodeDef;
import com.pnfsoftware.jeb.core.units.code.CodePointer;
import com.pnfsoftware.jeb.core.units.code.FlowInformation;
import com.pnfsoftware.jeb.core.units.code.IFlowInformation;
import com.pnfsoftware.jeb.core.units.code.IInstruction;
import com.pnfsoftware.jeb.core.units.code.InstructionFlags;
import com.pnfsoftware.jeb.util.serialization.annotations.Ser;
import com.pnfsoftware.jeb.util.serialization.annotations.SerId;

/**
 * Representation of a Diem instruction. 
 *
 * @author Nicolas Falliere
 *
 */
@Ser
public class DiemInstruction implements IInstruction {
    @SerId(1)
    OpcodeDef opdef;
    @SerId(2)
    byte[] code;
    /** 0 or 1 immediate operands; most operands of a an instruction are pushed on the operand stack */
    @SerId(3)
    DiemInstructionOperand[] opnds;

    @SerId(4)
    int preExecStackDelta;
    @SerId(5)
    int postExecStackDelta;
    @SerId(6)
    int indexInFunction;
    @SerId(7)
    int offsetInFunction;
    @SerId(8)
    int targetDelta;  // for branch instructions

    public DiemInstruction(Diem.OpcodeDef opdef) {
        this.opdef = opdef;
    }

    public OpcodeDef getOpcode() {
        return opdef;
    }

    @Override
    public int getProcessorMode() {
        return DiemUnit.ptrsize;
    }

    @Override
    public int getSize() {
        return code.length;
    }

    @Override
    public byte[] getCode() {
        return code;
    }

    @Override
    public String getMnemonic() {
        return opdef.toString();
    }

    @Override
    public DiemInstructionOperand[] getOperands() {
        return opnds;
    }

    public long getOperandAsLong() {
        return (long)opnds[0].getObject();
    }

    public int getOperandAsIndex() {
        return (int)opnds[0].getObject();
    }

    @Override
    public String getPrefix() {
        return null;
    }

    @Override
    public IFlowInformation getBreakingFlow(long instructionAddress) {
        if(opdef == OpcodeDef.RET) {
            return new FlowInformation();
        }
        // remember that those diemvm instructions store the absolute (within a routine) index of the target instruction
        // as the immediate operand of the branch instruction; the target is _not_ a relative offset or similar
        if(opdef == OpcodeDef.BR_TRUE || opdef == OpcodeDef.BR_FALSE || opdef == OpcodeDef.BRANCH) {
            FlowInformation f = new FlowInformation();
            // (optional) fallthrough
            if(opdef == OpcodeDef.BR_TRUE || opdef == OpcodeDef.BR_FALSE) {
                f.addTarget(new CodePointer(instructionAddress + getSize()));
            }
            // calculate the target as if it were an address, thanks to the target-delta previously stored by the bc parser
            f.addTarget(new CodePointer(instructionAddress + targetDelta));
            return f;
        }
        return FlowInformation.NONE;
    }

    @Override
    public IFlowInformation getRoutineCall(long instructionAddress) {
        if(opdef == OpcodeDef.CALL) {
            // not super important since we're dealing with pre-parsed bytecode routines
            // TODO: however will add else missing xrefs 
            return new FlowInformation();
        }
        return FlowInformation.NONE;
    }

    @Override
    public IFlowInformation collectIndirectCallReferences(long instructionAddress) {
        return FlowInformation.NONE;
    }

    @Override
    public void getDefUse(List<Integer> def, List<Integer> use, Object context) {
        // not implemented
    }

    @Override
    public boolean canThrow() {
        // not implemented
        return false;
    }

    @Override
    public Set<InstructionFlags> getInstructionFlags() {
        return Collections.emptySet();
    }

    @Override
    public String format(Object context) {
        long address = context instanceof Long ? (long)context: 0L;
        StringBuilder sb = new StringBuilder();
        sb.append(opdef.toString());
        if(opnds != null && opnds.length == 1) {
            sb.append(" ").append(opnds[0].format(this, address));
        }
        return String.format("%-30s     [%d,%d]", sb.toString(), preExecStackDelta, postExecStackDelta);
    }

    @Override
    public String toString() {
        return format(null);
    }
}
