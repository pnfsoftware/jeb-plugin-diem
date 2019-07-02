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

package com.pnf.libravm;

import com.pnfsoftware.jeb.core.output.ItemClassIdentifiers;
import com.pnfsoftware.jeb.core.output.code.CodeDocumentPart;
import com.pnfsoftware.jeb.core.units.code.IInstruction;
import com.pnfsoftware.jeb.core.units.code.IInstructionOperand;
import com.pnfsoftware.jeb.core.units.code.asm.render.GenericCodeFormatter;
import com.pnfsoftware.jeb.core.units.code.asm.render.NumberFormatter;
import com.pnfsoftware.jeb.util.format.Formatter;
import com.pnfsoftware.jeb.util.serialization.annotations.Ser;

/**
 * Custom formatting methods to render Libra disassembly.
 *
 * @author Nicolas Falliere
 *
 */
@Ser
public class LibraCodeFormatter extends GenericCodeFormatter<LibraInstruction> {

    public LibraCodeFormatter() {
        super();
        setMnemonicRightPaddingLength(0);
    }

    @Override
    public String generateExtraMethodComment(long address) {
        LibraUnit libra = (LibraUnit)getCodeUnit().getParent();
        FunctionDef f = libra.getFunctionByAddress(address);
        if(f == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Libra signature: %s / ", libra.formatObject(f.getHandle(libra).getSignature(libra))));
        sb.append(String.format("Locals: %s", libra.formatObject(f.getCode().getLocals(libra))));
        return sb.toString();
    }

    /** Render the pre- and post- execution stack deltas before the instruction. */
    @Override
    public void formatInstruction(long address, IInstruction insn, CodeDocumentPart out) {
        LibraInstruction _insn = (LibraInstruction)insn;

        // display operand stack information, before and after instruction execution
        String p = String.format("[%d,%d] ", _insn.preExecStackDelta, _insn.postExecStackDelta);
        out.appendAndRecord(p, ItemClassIdentifiers.COMMENT);
        out.append("  ");

        super.formatInstruction(address, insn, out);
    }

    @Override
    public void formatOperand(long address, IInstruction insn, IInstructionOperand opnd, int opndIndexGlobal,
            int opndDepth, CodeDocumentPart out) {
        LibraUnit libra = (LibraUnit)(getCodeUnit().getParent());
        LibraInstruction _insn = (LibraInstruction)insn;
        LibraInstructionOperand _opnd = (LibraInstructionOperand)opnd;

        Object o = _opnd.getObject();
        switch(_opnd.getOperandType()) {
        case Branch:
            long targetAddress = address + _insn.targetDelta;
            formatAddress(targetAddress, out);
            break;
        case ImmUint64:
            long val = (Long)o;
            NumberFormatter prefs = getNumberFormatter(opnd, false);
            if(prefs == null) {
                prefs = getDefaultNumberFormatter();
            }
            String s = prefs.format(64, val);
            out.appendAndRecord(s, ItemClassIdentifiers.IMMEDIATE, createItemIdForImmediate(address, opndIndexGlobal));
            break;
        case IdxLocal:
            out.append("@" + _opnd.getObject());
            break;
        case IdxAddress:
            //out.append("#" + _opnd.getObject());
            out.append(libra.addressPool.get((int)o).toString());
            break;
        case IdxByteArray:
            //out.append("#" + _opnd.getObject());
            out.append(libra.bytearrayPool.get((int)o).toString());
            break;
        case IdxString:
            //out.append("#" + _opnd.getObject());
            String str = libra.stringPool.get((int)o).toString();
            out.appendAndRecord(Formatter.escapeString(str), ItemClassIdentifiers.STRING);
            break;
        case IdxFieldDef:
            //out.append("#" + _opnd.getObject());
            out.append(libra.fieldDefs.get((int)o).getName(libra));
            break;
        case IdxFuncHandle:
            //out.append("#" + _opnd.getObject());
            //out.append(libra.formatObject(libra.functionHandles.get((int)o)));
            out.append(libra.functionHandles.get((int)o).getName(libra));
            break;
        case IdxStructDef:
            //out.append("#" + _opnd.getObject());
            out.append(libra.structDefs.get((int)o).getName(libra));
            break;
        default:
            out.append(opnd.format(insn, address));
            break;
        }
    }
}
