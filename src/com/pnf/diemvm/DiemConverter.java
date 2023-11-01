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

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import com.pnf.diemvm.Diem.OpcodeDef;
import com.pnf.diemvm.Diem.OpndType;
import com.pnfsoftware.jeb.core.units.INativeCodeUnit;
import com.pnfsoftware.jeb.core.units.code.asm.cfg.BasicBlock;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.AbstractConverter;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ConverterInstructionEntry;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.IEGlobalContext;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.IERoutineContext;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.exceptions.UnsupportedConversionException;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ir.IEAssign;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ir.IECall;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ir.IEGeneric;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ir.IEImm;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ir.IEPrototypeHandler;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ir.IEReturn;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ir.IEStatement;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ir.IEUntranslatedInstruction;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ir.IEVar;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ir.IWildcardType;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ir.IWildcardTypeManager;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ir.OperationType;
import com.pnfsoftware.jeb.core.units.code.asm.items.INativeFieldItem;
import com.pnfsoftware.jeb.core.units.code.asm.items.INativeMethodItem;
import com.pnfsoftware.jeb.core.units.code.asm.type.ICallingConvention;
import com.pnfsoftware.jeb.core.units.code.asm.type.INativeType;
import com.pnfsoftware.jeb.core.units.code.asm.type.IPrototypeItem;
import com.pnfsoftware.jeb.core.units.code.asm.type.IReferenceType;
import com.pnfsoftware.jeb.core.units.code.asm.type.ITypeManager;
import com.pnfsoftware.jeb.util.base.Assert;
import com.pnfsoftware.jeb.util.format.Formatter;
import com.pnfsoftware.jeb.util.logging.GlobalLog;
import com.pnfsoftware.jeb.util.logging.ILogger;
import com.pnfsoftware.jeb.util.serialization.annotations.Ser;
import com.pnfsoftware.jeb.util.serialization.annotations.SerId;
import com.pnfsoftware.jeb.util.serialization.annotations.SerTransient;

/**
 * Diem bytecode to JEB IR converter. This class is the most important component of a decompiler
 * plugin.
 *
 * @author Nicolas Falliere
 *
 */
@Ser
public class DiemConverter extends AbstractConverter<DiemInstruction> {
    private static final ILogger logger = GlobalLog.getLogger(DiemConverter.class);

    public static final int ID_PC = 0;
    public static final int ID_SP = 0x100;

    public static final String PFX_LOCAL = "local";
    public static final String PFX_PARAM = PFX_LOCAL;
    public static final String PFX_STACK = "var";

    @SerId(1)
    IEVar pc;
    @SerId(2)
    IEVar sp;

    @SerId(3)
    IEGlobalContext _gCtx;
    @SerId(4)
    DiemUnit unit;
    @SerId(5)
    INativeCodeUnit<DiemInstruction> pbcu;

    // only valid during a routine conversion phase
    @SerTransient
    FunctionDef functionDef;
    @SerTransient
    FunctionHandle functionHandle;
    /** localSlotVars = [params | locals] */
    @SerTransient
    List<IEVar> localSlotVars;

    static class StackSlot {
        IEVar var;
        SignatureToken st;

        public StackSlot(IEVar var, SignatureToken st) {
            this.var = var;
            this.st = st;
        }
    }

    @SerTransient
    private List<StackSlot> opndstack = new LinkedList<>();
    @SerTransient
    private int opndstackIndex = 0;
    @SerTransient
    private int opndstackCounter = 0;

    protected DiemConverter(DiemUnit unit, INativeCodeUnit<DiemInstruction> code) {
        super(unit.getBytecodeParser(), DiemUnit.ptrsize);
        this.unit = unit;

        if(code == null) {
            throw new IllegalArgumentException();
        }
        this.pbcu = code;

        pc = gCtx.createRegister(ID_PC, "pc", regNormalBitsize);
        sp = gCtx.createRegister(ID_SP, "sp", regNormalBitsize);  // unused
    }

    @Override
    public IEVar getProgramCounter() {
        return pc;
    }

    @Override
    public IEVar getStackPointer() {
        return sp;
    }

    private IEVar createVariable(String name, int bitsize) {
        // non-stack variables
        return ctx.createVirtualVar(name, bitsize);
    }

    @Override
    protected void preRoutineConversion(INativeMethodItem routine, IERoutineContext ctx, List<IEStatement> irlist) {
        autoAssignFunctionPrototypes();

        functionDef = unit.getFunctionByName(routine.getName());
        functionHandle = functionDef.getHandle(unit);

        // create EVars for parameters and locals
        // diem ABI: function parameters are placed into the first locals 0...N-1
        // (implies that the first N locals must have the types of the N parameters)
        LocalSignature locals = functionDef.getCode().getLocals(unit);
        int index = 0;
        localSlotVars = new ArrayList<>();
        for(SignatureToken token: locals.getTokens()) {
            IEVar slot = createVariable(PFX_LOCAL + index, getDiemTypeBitsize(token));
            // note: quite unorthodox - traditionally IR types are provided to GENDEC in must later stages, way after the initial conversion phases
            // (and it is the case for ex. for special IEUntranslatedInstruction, whose types are appropriately provided by the decompiler extension)
            // GENDEC may discard some types during the initial conversion, lifting, and optimization phases
            // however, I'm doing this here to get even better decompilation results
            slot.setType(ctx.getWildcardTypeManager().create(convertDiemType(token)));
            index++;
            localSlotVars.add(slot);
        }

        opndstack = new LinkedList<>();
        opndstackIndex = 0;
        opndstackCounter = 0;
    }

    @Override
    protected void postRoutineConversion(INativeMethodItem routine, IERoutineContext ctx) {
        // reset the transient attributes
        functionDef = null;
        functionHandle = null;
        localSlotVars = null;
        opndstack = null;
        opndstackIndex = 0;
        opndstackCounter = 0;
    }

    @Override
    protected void convertBlock(BasicBlock<DiemInstruction> b, List<IEStatement> interlist) {
        long base = b.getFirstAddress();
        long address = base;
        List<IEStatement> r = new ArrayList<>();
        ConverterInstructionEntry<DiemInstruction> e = new ConverterInstructionEntry<>();
        e.r = r;  // will not change

        DiemInstruction insn = null;
        try {
            int i = 0;
            while(i < b.size()) {

                // current native instruction
                insn = b.get(i);

                // block entry, let's pull the current stack index in the method
                if(i == 0) {
                    opndstackIndex = insn.preExecStackDelta;
                }

                r.clear();
                int irAddress = interlist.size();

                e.insn = insn;
                e.address = address;
                e.irAddress = irAddress;

                OpcodeDef opcode = insn.getOpcode();
                switch(opcode) {
                case LD_CONST: {
                    long val = insn.getOperandAsLong();
                    pushAssign(e, SignatureToken.stUint64, ctx.createImm(val, 64));
                    break;
                }
                case ST_LOC: {
                    int idx = insn.getOperandAsIndex();
                    IEVar dst = getLocalSlot(idx);
                    IEVar var = pop();
                    e.r.add(ctx.createAssign(dst, var));
                    break;
                }
                case LD_FALSE:
                case LD_TRUE: {
                    pushAssign(e, SignatureToken.stBool, ctx.createImm(opcode == OpcodeDef.LD_FALSE ? 0: 1, 64));
                    break;
                }
                case COPY_LOC:
                case MOVE_LOC: {
                    int idx = insn.getOperandAsIndex();
                    IEVar var = getLocalSlot(idx);
                    SignatureToken sig = functionDef.getCode().getLocals(unit).getTokens().get(idx);
                    pushAssign(e, sig, var);
                    if(opcode == OpcodeDef.MOVE_LOC) {
                        // TODO: limited translation for move: we make the location invalid by zero'ing it (although zero is not invalid per-say)
                        e.r.add(ctx.createAssign(var, ctx.createImm(0, var.getBitsize())));
                    }
                    break;
                }
                case BR_FALSE:
                case BR_TRUE: {
                    IEGeneric cond = pop();
                    if(opcode == OpcodeDef.BR_FALSE) {
                        cond = ctx.createOperation(OperationType.LOG_NOT, cond);
                    }
                    long ftNativeAddress = e.address + insn.getSize();
                    long brNativeAddress = insn.getBreakingFlow(e.address).getTargets().get(1).getAddress();
                    IEAssign stm = ctx.createBranchAssign(pc, ctx.createCond(cond, ctx.createImm(brNativeAddress, 64),
                            ctx.createImm(ftNativeAddress, 64)), false);
                    e.r.add(stm);
                    break;
                }
                case BRANCH: {
                    long brNativeAddress = insn.getBreakingFlow(e.address).getTargets().get(0).getAddress();
                    IEAssign stm = ctx.createBranchAssign(pc, ctx.createImm(brNativeAddress, 64), false);
                    e.r.add(stm);
                    break;
                }
                case NOT: {
                    OperationType optype = opcodeToOperationType(opcode);
                    IEGeneric opnd = pop();
                    IEGeneric res = ctx.createOperation(optype, opnd);
                    res = res.zeroExtend(64);
                    pushAssign(e, SignatureToken.stUint64, res);
                    break;
                }
                case ADD:
                case SUB:
                case MUL:
                case MOD:
                case DIV:
                case BIT_OR:
                case BIT_AND:
                case XOR:
                case OR:
                case AND:
                case EQ:
                case NEQ:
                case LT:
                case GT:
                case LE:
                case GE: {
                    OperationType optype = opcodeToOperationType(opcode);
                    IEGeneric opnd1 = pop();
                    IEGeneric opnd0 = pop();
                    IEGeneric res = ctx.createOperation(optype, opnd0, opnd1);

                    // TODO: proper support for 1-bit booleans to avoid some unnecessary casts
                    SignatureToken token;
                    //if(opcode.isBinaryOperation()) {
                    //    token = SignatureToken.stUint64;
                    //}
                    //else if(opcode.isLogicalOperation()) {
                    //    token = SignatureToken.stBool;
                    //}
                    //else {
                    //    throw new RuntimeException();
                    //}

                    // temporary
                    token = SignatureToken.stUint64;
                    res = res.zeroExtend(64);

                    pushAssign(e, token, res);
                    break;
                }
                case RET: {
                    IEReturn ret;
                    List<SignatureToken> returnTokens = functionHandle.getSignature(unit).getReturnTokens();
                    if(returnTokens.isEmpty()) {
                        ret = ctx.createReturn();
                    }
                    else if(returnTokens.size() == 1) {
                        IEVar retvar = pop();
                        ret = ctx.createReturn(retvar);
                    }
                    else {
                        List<IEGeneric> retvars = new ArrayList<>(returnTokens.size());
                        for(int itoken = 0; itoken < returnTokens.size(); itoken++) {
                            retvars.add(0, pop());
                        }
                        ret = ctx.createReturn(retvars);
                    }
                    e.r.add(ret);
                    break;
                }
                case POP: {
                    pop();
                    break;
                }
                case CALL: {
                    int idx = insn.getOperandAsIndex();
                    FunctionHandle f = unit.functionHandles.get(idx);
                    String fname = f.getName(unit);
                    FunctionSignature fsig = f.getSignature(unit);

                    INativeMethodItem targetRoutine = getNativeContext().getRoutine(f.mappedAddress);
                    if(targetRoutine == null) {
                        targetRoutine = getNativeContext().getRoutineByName(fname);
                        if(targetRoutine == null) {
                            throw new UnsupportedConversionException("Cannot resolve routine");
                        }
                    }

                    IPrototypeItem proto = convertDiemPrototype(fsig);
                    if(targetRoutine.getPrototype() == null) {
                        targetRoutine.setPrototype(proto);
                    }

                    List<IEGeneric> _paramExp = new ArrayList<>();
                    for(@SuppressWarnings("unused")
                    SignatureToken token: fsig.getParamTokens()) {
                        _paramExp.add(0, pop());
                    }

                    List<IEGeneric> _returnExp = new ArrayList<>();
                    for(SignatureToken token: fsig.getReturnTokens()) {
                        _returnExp.add(push(token));
                    }

                    IEVar callSite = ctx.createSymbolForRoutine(targetRoutine);

                    IECall call = ctx.createCall(callSite, null, _returnExp, _paramExp, 0, null, null);
                    e.r.add(call);
                    break;
                }
                case LD_ADDR: {
                    int idx = insn.getOperandAsIndex();
                    byte[] bytes = unit.addressPool.get(idx).getBytes();
                    IEImm addr = ctx.createImm(bytes, 256);
                    pushAssign(e, SignatureToken.stAddress, addr);
                    break;
                }
                case LD_BYTEARRAY: {
                    int idx = insn.getOperandAsIndex();
                    long addr = unit.bytearrayPool.get(idx).mappedAddress;
                    INativeFieldItem item = getNativeContext().getField(addr);
                    IEVar symbol = ctx.createSymbolForField(item);
                    pushAssign(e, SignatureToken.stBytearray, symbol);
                    break;
                }
                case LD_STR: {
                    int idx = insn.getOperandAsIndex();
                    long addr = unit.stringPool.get(idx).mappedAddress;
                    INativeFieldItem item = getNativeContext().getField(addr);
                    IEVar symbol = ctx.createSymbolForField(item);
                    pushAssign(e, SignatureToken.stString, symbol);
                    break;
                }

                // all following instructions cannot be directly translated to clean IR
                // we use IEUntranslatedInstruction (~ IECall on steroid) 

                case BORROW_REF: {  //=BorrowGlobal
                    int idx = insn.getOperandAsIndex();
                    StructDef sd = unit.structDefs.get(idx);

                    IEVar arg_addr = pop();

                    SignatureToken token = new SignatureToken(sd.getHandleIndex());
                    token = new SignatureToken(token, true);
                    IEVar res = push(token);

                    e.r.add(createUntranslated(ctx, address, insn, res, arg_addr));
                    break;
                }
                case FREEZE_REF: {
                    IEVar arg_mutref = pop();
                    IEVar res = pushForce(arg_mutref.getType());
                    e.r.add(createUntranslated(ctx, address, insn, res, arg_mutref));
                    break;
                }
                case LD_REF_FIELD: {  // =BorrowField
                    int idx = insn.getOperandAsIndex();
                    FieldDef field = unit.fieldDefs.get(idx);

                    IEVar arg_ref = pop();

                    SignatureToken token = field.getSignature(unit).getToken();
                    token = new SignatureToken(token, true);

                    long fieldNameAddr = unit.stringPool.get(field.name_index).mappedAddress;
                    INativeFieldItem fieldNameItem = getNativeContext().getField(fieldNameAddr);
                    IEVar arg_fieldname = ctx.createSymbolForField(fieldNameItem);

                    IEVar res = push(token);

                    e.r.add(createUntranslated(ctx, address, insn, res, arg_ref, arg_fieldname));
                    break;
                }
                case READ_REF: { //*
                    IEVar arg_ref = pop();

                    // arg_ref should have a type: we're setting types early on in this converter
                    IEVar res;
                    IWildcardType t = arg_ref.getType();
                    if(t != null && t.getNativeType() instanceof IReferenceType) {
                        INativeType nt = ((IReferenceType)t.getNativeType()).getPointedType();
                        t = ctx.getWildcardTypeManager().create(nt);
                        res = pushForce(t);//SignatureToken.stAnyMutableRef);
                    }
                    else {
                        res = push(SignatureToken.stAnyMutableRef);
                    }
                    e.r.add(createUntranslated(ctx, address, insn, res, arg_ref));
                    break;
                }
                case WRITE_REF: {
                    IEVar refval = pop();
                    IEVar val = pop();
                    e.r.add(createUntranslated(ctx, address, insn, null, refval, val));
                    break;
                }
                case PACK: {
                    int idx = insn.getOperandAsIndex();
                    StructDef sd = unit.structDefs.get(idx);
                    int popcnt = sd.getFieldCount();
                    IEGeneric[] opnds = new IEGeneric[popcnt];
                    for(int opndindex = 0; opndindex < popcnt; opndindex++) {
                        opnds[popcnt - 1 - opndindex] = pop();
                    }

                    SignatureToken token = new SignatureToken(sd.getHandleIndex());
                    token = new SignatureToken(token, true);

                    IEGeneric res = push(token);
                    e.r.add(createUntranslated(ctx, address, insn, res, opnds));
                    break;
                }
                case UNPACK: {
                    int idx = insn.getOperandAsIndex();
                    StructDef sd = unit.structDefs.get(idx);
                    IEVar instance = pop();

                    int pushcnt = sd.getFieldCount();
                    List<IEGeneric> retvals = new ArrayList<>(pushcnt);
                    for(int fi = 0; fi < pushcnt; fi++) {
                        retvals.add(push(sd.getFields(unit).get(fi).getSignature(unit).getToken()));
                    }

                    IEUntranslatedInstruction ir = createUntranslated(ctx, address, insn, null, instance);
                    ir.setReturnExpressions(retvals);
                    e.r.add(ir);

                    break;
                }
                case MOVE_TO: {  //=MoveToSender
                    int idx = insn.getOperandAsIndex();
                    @SuppressWarnings("unused")  // TODO: provide struct info
                    StructDef sd = unit.structDefs.get(idx);
                    IEVar arg_addr = pop();
                    //createVariable("pseudoVar", 64);
                    e.r.add(createUntranslated(ctx, address, insn, null, arg_addr));
                    break;
                }
                case MOVE_FROM: {
                    int idx = insn.getOperandAsIndex();
                    @SuppressWarnings("unused")  // TODO: provide struct info
                    StructDef sd = unit.structDefs.get(idx);
                    IEVar arg_addr = pop();
                    //createVariable("pseudoVar", 64);
                    e.r.add(createUntranslated(ctx, address, insn, null, arg_addr));
                    break;
                }
                case EXISTS: {
                    int idx = insn.getOperandAsIndex();
                    @SuppressWarnings("unused")  // TODO: provide struct info
                    StructDef sd = unit.structDefs.get(idx);
                    IEVar arg_addr = pop();
                    IEVar res = push(SignatureToken.stBool);
                    e.r.add(createUntranslated(ctx, address, insn, res, arg_addr));
                    break;
                }
                case LD_REF_LOC: {
                    int idx = insn.getOperandAsIndex();
                    SignatureToken token = functionDef.getCode().getLocals(unit).getTokens().get(idx);
                    token = new SignatureToken(token, false);
                    IEVar local = getLocalSlot(idx);
                    IEVar res = push(token);
                    e.r.add(createUntranslated(ctx, address, insn, res, local));
                    break;
                }
                // generic generation of IEUntranslatedInstruction for 1) special instructions 2) with no immediate operands 3) and at most one ret.val
                case ASSERT:
                case EMIT_EVENT:
                case GET_GAS_REMAINING:
                case GET_TXN_GAS_UNIT_PRICE:
                case GET_TXN_MAX_GAS_UNITS:
                case GET_TXN_PUBLIC_KEY:
                case GET_TXN_SENDER:
                case GET_TXN_SEQUENCE_NUMBER:
                case CREATE_ACCOUNT:
                case RELEASE_REF: {
                    Assert.a(insn.getOpcode().getOperandType() == OpndType.None);
                    int popcnt = insn.getOpcode().getPopCount();
                    int pushcnt = insn.getOpcode().getPushCount();

                    IEGeneric[] opnds = new IEGeneric[popcnt];
                    for(int opndindex = 0; opndindex < popcnt; opndindex++) {
                        opnds[popcnt - 1 - opndindex] = pop();  // diem convention, arguments are pushed from 1st to last, so we're pop'ing teh last first
                    }
                    IEGeneric res = null;

                    Assert.a(pushcnt <= 1);
                    if(pushcnt == 1) {
                        switch(opcode) {
                        case EXISTS:
                            res = push(SignatureToken.stBool);
                            break;
                        case GET_TXN_SEQUENCE_NUMBER:
                        case GET_GAS_REMAINING:
                        case GET_TXN_GAS_UNIT_PRICE:
                        case GET_TXN_MAX_GAS_UNITS:
                            res = push(SignatureToken.stUint64);
                            break;
                        case GET_TXN_SENDER:
                            res = push(SignatureToken.stAddress);
                            break;
                        case GET_TXN_PUBLIC_KEY:
                            res = push(SignatureToken.stBytearray);
                            break;
                        default:
                            throw new RuntimeException("TBI: return type for " + opcode);
                        }
                    }

                    e.r.add(createUntranslated(ctx, address, insn, res, opnds));
                    break;
                }
                default:
                    throw new UnsupportedConversionException(
                            String.format("Cannot convert instruction: %s", insn.getOpcode().getHLMnemonic()));
                }
                //@formatter:on

                // note: "size" of an IR statement is set to 1 here (therefore, first address is: 0, second: 1, etc)
                for(IEStatement stm: r) {
                    logger.i("0x%X -> %s", address, stm);
                    stm.setLowerLevelAddress(address);
                    interlist.add(stm);
                }

                // next native instruction in the block
                address += insn.getSize();
                i++;
            }
        }
        catch(Throwable ex) {
            logger.error("Error: Instruction cannot be converted: %Xh: %s: %s", address,
                    Formatter.byteArrayToHexString(insn.getCode()), insn.format(address));
            logger.catchingSilent(ex);
            throw ex;
        }
        finally {
            //IRE.setSkipDuplicateValidation(dupval);
        }
    }

    OperationType opcodeToOperationType(OpcodeDef opcode) {
        switch(opcode) {
        case ADD:
            return OperationType.ADD;
        case SUB:
            return OperationType.SUB;
        case MUL:
            return OperationType.MUL_U;
        case MOD:
            return OperationType.REM_U;
        case DIV:
            return OperationType.DIV_U;
        case BIT_OR:
            return OperationType.OR;
        case BIT_AND:
            return OperationType.AND;
        case XOR:
            return OperationType.XOR;
        case OR:
            return OperationType.LOG_OR;
        case AND:
            return OperationType.LOG_AND;
        case EQ:
            return OperationType.LOG_EQ;
        case NEQ:
            return OperationType.LOG_NEQ;
        case LT:
            return OperationType.LT_U;
        case GT:
            return OperationType.GT_U;
        case LE:
            return OperationType.LE_U;
        case GE:
            return OperationType.GE_U;
        case NOT:
            return OperationType.LOG_NOT;
        default:
            throw new RuntimeException("TBI: conversion for operator: " + opcode);
        }
    }

    IEVar getLocalSlot(int index) {
        return localSlotVars.get(index);
    }

    void pushAssign(ConverterInstructionEntry<DiemInstruction> e, SignatureToken st, IEGeneric expression) {
        IEVar stkvar;
        if(st == null) {
            stkvar = pushForce(expression.getBitsize());
        }
        else {
            stkvar = push(st);
        }
        e.r.add(ctx.createAssign(stkvar, expression));
    }

    IEVar pushForce(int bitsize) {
        IEVar stkvar;
        if(opndstackIndex < opndstack.size()) {
            stkvar = createVariable(PFX_STACK + opndstackCounter, bitsize);
            opndstack.set(opndstackIndex, new StackSlot(stkvar, null));
            opndstackCounter++;
        }
        else {
            stkvar = createVariable(PFX_STACK + opndstackCounter, bitsize);
            opndstack.add(new StackSlot(stkvar, null));
            opndstackCounter++;
        }
        opndstackIndex++;
        return stkvar;
    }

    IEVar pushForce(IWildcardType type) {
        IEVar stkvar;
        if(opndstackIndex < opndstack.size()) {
            stkvar = createVariable(PFX_STACK + opndstackCounter, type.getBitsize());
            opndstack.set(opndstackIndex, new StackSlot(stkvar, null));
            opndstackCounter++;
        }
        else {
            stkvar = createVariable(PFX_STACK + opndstackCounter, type.getBitsize());
            opndstack.add(new StackSlot(stkvar, null));
            opndstackCounter++;
        }
        stkvar.setType(type);
        opndstackIndex++;
        return stkvar;
    }

    IEVar push(SignatureToken st) {
        Assert.a(st != null);

        INativeType nativeType = convertDiemType(st);
        IWildcardType type = ctx.getWildcardTypeManager().create(nativeType);

        IEVar stkvar;
        if(opndstackIndex < opndstack.size()) {
            StackSlot slot = opndstack.get(opndstackIndex);
            if(slot.st != null && slot.st.equals(st)) {
                stkvar = slot.var;
            }
            else {
                stkvar = createVariable(PFX_STACK + opndstackCounter, type.getBitsize()/*getDiemTypeBitsize(st)*/);
                stkvar.setType(type);
                opndstack.set(opndstackIndex, new StackSlot(stkvar, st));
                opndstackCounter++;
            }
        }
        else {
            stkvar = createVariable(PFX_STACK + opndstackCounter, type.getBitsize()/*getDiemTypeBitsize(st)*/);
            stkvar.setType(type);
            opndstack.add(new StackSlot(stkvar, st));
            opndstackCounter++;
        }
        opndstackIndex++;
        return stkvar;
    }

    IEVar pop() {
        Assert.a(opndstackIndex > 0);
        opndstackIndex--;
        return opndstack.get(opndstackIndex).var;
    }

    IEVar peek() {
        Assert.a(opndstackIndex > 0);
        return opndstack.get(opndstackIndex - 1).var;
    }

    @Override
    public int insertReturns(IERoutineContext _ctx) {
        // we bypass the IEReturn insertion phase: the IEReturn statements were inserted when converting the RET opcode
        // (this stage would fail anyway since we do not have a proper calling convention for diem binaries)
        return 0;
    }

    void autoAssignFunctionPrototypes() {
        for(INativeMethodItem routine: pbcu.getMethods()) {
            autoAssignFunctionPrototype(routine, false);
        }
    }

    IPrototypeItem autoAssignFunctionPrototype(INativeMethodItem routine, boolean force) {
        IPrototypeItem proto = routine.getPrototype();
        if(proto == null || force) {
            FunctionHandle f = unit.getFunctionHandleByName(routine.getName(false));
            proto = convertDiemPrototype(f.getSignature(unit));
            routine.setPrototype(proto);
        }
        return proto;
    }

    IPrototypeItem convertDiemPrototype(FunctionSignature fsig) {
        // retrieve the default calling convention determined by the code analyzer plugin - it should be UNKNOWN (any)
        ICallingConvention cc = pbcu.getTypeManager().getCallingConventionManager().getDefaultConvention();

        List<INativeType> nativeParameterTypes = new ArrayList<>();
        for(SignatureToken token: fsig.getParamTokens()) {
            INativeType t = convertDiemType(token);
            nativeParameterTypes.add(t);
        }

        List<INativeType> nativeReturnTypes = new ArrayList<>();
        for(SignatureToken token: fsig.getReturnTokens()) {
            INativeType t = convertDiemType(token);
            nativeReturnTypes.add(t);
        }

        if(fsig.getReturnTokens().size() >= 2) {
            logger.debug("take not: method has 2+ return values: %s", unit.formatObject(fsig));
        }
        return pbcu.getTypeManager().createPrototypeEx(cc, nativeReturnTypes, nativeParameterTypes, null);
    }

    int getDiemTypeBitsize(SignatureToken st) {
        switch(st.getSerializedType()) {
        case BOOL:
            return 64;  // make it a regular uint64
        case INTEGER:
            return 64;
        case MUTABLE_REFERENCE:
            return 64;
        case REFERENCE:
            return 64;
        case ADDRESS:
            return 256;
        case BYTEARRAY:
            return 64;  // make it a reference to
        case STRING:
            return 64;  // make it a reference to
        case STRUCT:
            return 64;  // make it a reference to
        default:
            throw new RuntimeException();
        }
    }

    INativeType convertDiemType(SignatureToken token) {
        ITypeManager typeman = pbcu.getTypeManager();
        //IPrimitiveTypeManager pman = typeman.getPrimitives();

        switch(token.getSerializedType()) {
        case BOOL:
            return typeman.getType("bool");
        case INTEGER:
            return typeman.getType("u64");
        case ADDRESS:
            return typeman.getType("address");
        case BYTEARRAY:
            return typeman.getType("bytearray");
        case STRING:
            return typeman.getType("string");
        case STRUCT:
            StructHandle sh = token.getStructureHandle(unit);
            String sname = sh.getFullName(unit).replace('@', '_').replace('.', '_');
            INativeType stype = typeman.getType(sname);
            if(stype == null) {
                stype = typeman.createStructure(sname);
            }
            return stype.getReference();
        case REFERENCE:
        case MUTABLE_REFERENCE:
            SignatureToken token0 = token.getReference();
            if(token0 == null) {
                return typeman.getVoidReference();
            }
            INativeType t = convertDiemType(token0);
            return typeman.createReference(t);
        default:
            throw new RuntimeException("TBI: type conversion: " + token);
        }
    }

    List<IEVar> getIRParameterVariables(IERoutineContext ctx, FunctionHandle fh) {
        IWildcardTypeManager etypeman = ctx.getWildcardTypeManager();
        List<IEVar> r = new ArrayList<>();
        List<SignatureToken> tokens = fh.getSignature(unit).getParamTokens();
        for(int index = 0; index < tokens.size(); index++) {
            IEVar var = ctx.getVariableByName(PFX_PARAM + index);
            var.setType(etypeman.create(convertDiemType(tokens.get(index))));
            r.add(var);
        }
        return r;
    }

    List<IWildcardType> getIRReturnTypes(IERoutineContext ctx, FunctionHandle fh) {
        IWildcardTypeManager etypeman = ctx.getWildcardTypeManager();
        List<IWildcardType> r = new ArrayList<>();
        for(SignatureToken token: fh.getSignature(unit).getReturnTokens()) {
            r.add(etypeman.create(convertDiemType(token)));
        }
        return r;
    }

    @Override
    public IEPrototypeHandler getPrototypeHandler(IERoutineContext ctx) {
        return new ProtoHandler(ctx);
    }

    private class ProtoHandler implements IEPrototypeHandler {
        IERoutineContext ctx;

        ProtoHandler(IERoutineContext ctx) {
            this.ctx = ctx;
        }

        @Override
        public boolean applyKnownPrototype(boolean createCopies) {
            return true;
        }

        @Override
        public boolean retrieveFromPrototype(List<IEGeneric> params, List<IWildcardType> rettypes) {
            FunctionHandle fh = unit.getFunctionHandleByName(ctx.getRoutine().getName());
            params.addAll(getIRParameterVariables(ctx, fh));
            rettypes.addAll(getIRReturnTypes(ctx, fh));
            return true;
        }

        @Override
        public boolean refinePrototype() {
            return false;  // pass
        }
    }

    IEUntranslatedInstruction createUntranslated(IERoutineContext ctx, long nativeAddress, DiemInstruction insn,
            IEGeneric irResult, IEGeneric... irOperands) {
        String mn = insn.getOpcode().getHLMnemonic();
        IEUntranslatedInstruction _1 = ctx.createUntranslatedInstruction(nativeAddress, mn, irOperands);
        _1.setTag(insn.getOpcode().getOpcode());
        _1.setReturnExpression(irResult);
        return _1;
    }
}
