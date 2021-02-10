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

import static com.pnf.diemvm.Diem.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.pnfsoftware.jeb.core.units.code.asm.memory.IVirtualMemory;
import com.pnfsoftware.jeb.core.units.code.asm.processor.AbstractProcessor;
import com.pnfsoftware.jeb.core.units.code.asm.processor.IProcessor;
import com.pnfsoftware.jeb.core.units.code.asm.processor.ProcessorException;
import com.pnfsoftware.jeb.util.io.ByteArray;
import com.pnfsoftware.jeb.util.io.Endianness;
import com.pnfsoftware.jeb.util.logging.GlobalLog;
import com.pnfsoftware.jeb.util.logging.ILogger;
import com.pnfsoftware.jeb.util.serialization.annotations.Ser;
import com.pnfsoftware.jeb.util.serialization.annotations.SerId;

/**
 * Diem bytecode parser.
 * <p>
 * Note that, unlike what native code plugins would do, this parser is used by the {@link DiemUnit}
 * components (~ ELF, PE) to pre-parse methods, instead of really letting JEB native code analysis
 * pipeline use it when it sees fit. That is because we are dealing with managed code, whose method
 * instructions and boundaries are well-defined and reference other Diem pool items by indices,
 * which the generic pipeline has no clue about. By parsing earlier in the pipeline process, we can
 * construct an adequate representation of the diem module. Note that this design pattern of early
 * processing is common to all managed bytecodes (I designed the Ethereum and WebAssembly modules
 * similarly).
 * <p>
 * Ref: {@link https://github.com/diem/diem/blob/master/language/vm/src/file_format.rs}
 *
 * @author Nicolas Falliere
 *
 */
@Ser
public class DiemBytecodeParser extends AbstractProcessor<DiemInstruction> {
    private static final ILogger logger = GlobalLog.getLogger(DiemBytecodeParser.class);

    @SerId(1)
    DiemUnit unit;

    public DiemBytecodeParser() {
        this(null);
    }

    public DiemBytecodeParser(DiemUnit unit) {
        super(1, DiemUnit.ptrsize, Endianness.LITTLE_ENDIAN, 1);
        this.unit = unit;
    }

    /**
     * Parse and perform trivial stack analysis of a method.
     * <p>
     * Not an {@link IProcessor} method. Usage restricted to {@link DiemUnit}.
     */
    public List<DiemInstruction> parseFunction(int fh_index, int insncnt, int offset, int endOffset)
            throws ProcessorException {
        if(unit == null) {
            throw new IllegalStateException("Reserved usage");
        }

        int insize = unit.getFunctionSignature(fh_index).getParamTokens().size();
        int outsize = unit.getFunctionSignature(fh_index).getReturnTokens().size();
        logger.i("=> Function: %s (fh=%d): in=%d, out=%d", unit.getFunctionName(fh_index), fh_index, insize, outsize);

        final int start = offset;
        List<DiemInstruction> insnlist = new ArrayList<>(insncnt);
        List<Integer> branchInstructionsIndices = new ArrayList<>();
        int stkdelta = 0;

        for(int i = 0; i < insncnt; i++) {
            DiemInstruction insn = parseAt(unit.rawbytes, offset, endOffset);
            insn.preExecStackDelta = stkdelta;
            insn.indexInFunction = i;
            insn.offsetInFunction = offset - start;
            insnlist.add(insn);

            OpcodeDef opcode = insn.getOpcode();
            int popcnt = opcode.getPopCount();
            int pushcnt = opcode.getPushCount();
            switch(opcode) {
            case RET: {
                popcnt = outsize;
                break;
            }
            case PACK: {
                int sd_index = insn.getOperandAsIndex();
                popcnt = unit.getStructFieldCount(sd_index);
                break;
            }
            case UNPACK: {
                int sd_index = insn.getOperandAsIndex();
                pushcnt = unit.getStructFieldCount(sd_index);
                break;
            }
            case CALL: {
                int target_fh_index = insn.getOperandAsIndex();
                popcnt = unit.getFunctionSignature(target_fh_index).getParamTokens().size();
                pushcnt = unit.getFunctionSignature(target_fh_index).getReturnTokens().size();
                break;
            }
            default:
                ;
            }
            if(popcnt < 0 || pushcnt < 0) {
                throw new RuntimeException("TBI: stkdelta for " + opcode);
            }
            // stack consumption 
            stkdelta -= popcnt;
            if(stkdelta < 0) {
                throw new RuntimeException("Illegal stack delta: " + stkdelta);
            }
            // stack production
            stkdelta += pushcnt;
            insn.postExecStackDelta = stkdelta;

            // this instruction is branching, record it for later post-processing
            switch(insn.getOpcode()) {
            case BRANCH:
            case BR_FALSE:
            case BR_TRUE:
                branchInstructionsIndices.add(i);
                break;
            default:
                ;
            }

            // debug log
            logger.i("#%d/%04X/%04X: %s", i, offset, offset - start, insn.format((long)offset));

            // next instruction
            offset += insn.getSize();
            if(offset > endOffset) {
                throw new ArrayIndexOutOfBoundsException("Bytecode parsing is passing the buffer boundary");
            }
        }

        if(stkdelta != 0) {
            throw new RuntimeException("Unepxected non-zero stack delta at routine end: " + stkdelta);
        }

        // post-process branch instructions: determine the jump deltas
        // (diem branch instructions specify an instruction index (within the function) as the target;
        // JEB's IFlowInformation requires absolute memory address)
        for(int i: branchInstructionsIndices) {
            DiemInstruction insn = insnlist.get(i);
            int targetInstructionIndex = (int)insn.getOperands()[0].getObject();
            insn.targetDelta = insnlist.get(targetInstructionIndex).offsetInFunction - insn.offsetInFunction;
        }

        // TODO: stack consistency: verify that SP-deltas pre-exec on block entries are consistent with SP-deltas post-exec at exit of incoming blocks
        // also check whether of not the diem verifier performs this check already 
        return insnlist;
    }

    /**
     * Used by the code unit components. Instead of (re-)parsing, we find the routine that contains
     * the instruction and return it directly.
     */
    @Override
    public DiemInstruction parseAt(IVirtualMemory vm, long address) throws ProcessorException {
        if(unit == null) {
            throw new IllegalStateException("Reserved usage");
        }

        for(FunctionDef e: unit.getInternalFunctions()) {
            if(address >= e.mappedAddress && address < (e.mappedAddress + e.mappedSize)) {
                int wantedOffset = (int)(address - e.mappedAddress);
                int currentOffset = 0;
                for(DiemInstruction insn: e.getCode().getInstructions()) {
                    if(currentOffset == wantedOffset) {
                        return insn;
                    }
                    currentOffset += insn.getSize();
                }
                break;
            }
        }
        throw new ProcessorException(String.format("Cannot find preparsed instruction at address 0x%X", address));
    }

    @Override
    protected DiemInstruction parseAtInternal(byte[] bytes, final int index, final int end) throws ProcessorException {
        ByteArray ba = new ByteArray(bytes, index);
        int b = ba.u8();
        OpcodeDef opdef = OpcodeDef.fromValue(b);

        DiemInstruction insn = new DiemInstruction(opdef);
        DiemInstructionOperand opnd = null;

        OpndType opndtype = opdef.getOperandType();
        switch(opndtype) {
        case None:
            break;
        case Branch:
            int target = ba.u16();
            opnd = new DiemInstructionOperand(opndtype, target);
            break;
        case ImmUint64:
            long cst = ba.i64();  // NOTE: reading as signed (Java), although should be u64
            opnd = new DiemInstructionOperand(opndtype, cst);
            break;

        case IdxLocal:
            int local_index = ba.u8();  // WATCH OUT! u8, not varu16! (and that means no more than 256 locals per diem object)
            opnd = new DiemInstructionOperand(opndtype, local_index);
            break;
        case IdxAddress:
        case IdxByteArray:
        case IdxString:
        case IdxFuncHandle:
        case IdxFieldDef:
        case IdxStructDef:
            int idx = ba.varu16();
            opnd = new DiemInstructionOperand(opndtype, idx);
            break;
        default:
            throw new RuntimeException("Unsupported operand type " + opndtype + " (used by opcode " + opdef + " )");
        }

        insn.code = Arrays.copyOfRange(bytes, index, ba.position());
        if(opnd == null) {
            insn.opnds = new DiemInstructionOperand[]{};
        }
        else {
            insn.opnds = new DiemInstructionOperand[]{opnd};
        }
        return insn;
    }
}
