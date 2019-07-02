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

import com.pnfsoftware.jeb.util.base.Assert;

/**
 * A collection of Libra enumerations, including Move opcode definitions.  
 *
 * @author Nicolas Falliere
 *
 */
public class Libra {
    //@formatter:off

    public static final int ADDRESS_LENGTH = 0x20;

    static enum BinaryType {
        MODULE,
        SCRIPT
    }

    /** function flag */
    public static final int PUBLIC = 1;
    /** function flag */
    public static final int NATIVE = 2;

    public static String formatFunctionFlags(int flags) {
        String s = "";
        while(flags != 0) {
            if((flags & Libra.PUBLIC) != 0) {
                s += "public ";
                flags &= ~Libra.PUBLIC;
            }
            else if((flags & Libra.NATIVE) != 0) {
                s += "native ";
                flags &= ~Libra.NATIVE;
            }
            else {
                s += String.format("(0x%X) ", flags);
                flags = 0;
            }
        }
        return s.trim();
    }

    static enum TableType {
        MODULE_HANDLES(0x1),
        STRUCT_HANDLES(0x2),
        FUNCTION_HANDLES(0x3),
        ADDRESS_POOL(0x4),
        STRING_POOL(0x5),
        BYTE_ARRAY_POOL(0x6),
        MAIN(0x7, 2),              // only in script
        STRUCT_DEFS(0x8, 1),       // only in module
        FIELD_DEFS(0x9, 1),        // only in module
        FUNCTION_DEFS(0xA, 1),     // only in module
        TYPE_SIGNATURES(0xB),
        FUNCTION_SIGNATURES(0xC),
        LOCALS_SIGNATURES(0xD);

        int value;
        /** 0=common, 1=module, 2=script */
        int usage;

        private TableType(int value) {
            this(value, 0);
        }

        private TableType(int value, int usage) {
            this.value = value;
            this.usage = usage;
        }

        public int getValue() {
            return value;
        }

        public int getUsage() {
            return usage;
        }

        public static TableType fromValue(int value) {
            for(TableType t: values()) {
                if(t.value == value) {
                    return t;
                }
            }
            throw new RuntimeException("Unknown value: " + value);
        }
    }

    enum SignatureType {
        TYPE_SIGNATURE(0x01),
        FUNCTION_SIGNATURE(0x02),
        LOCAL_SIGNATURE(0x03);

        int value;

        private SignatureType(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static SignatureType fromValue(int value) {
            for(SignatureType t: values()) {
                if(t.value == value) {
                    return t;
                }
            }
            throw new RuntimeException("Unknown value: " + value);
        }
    }

    enum SerializedType {
        BOOL(0x01),
        INTEGER(0x02),
        STRING(0x03),
        ADDRESS(0x04),
        REFERENCE(0x05),
        MUTABLE_REFERENCE(0x06),
        STRUCT(0x07),
        BYTEARRAY(0x08);

        int value;

        private SerializedType(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        @Override
        public String toString() {
            switch(this) {
            case REFERENCE: return "&";
            case MUTABLE_REFERENCE: return "&mut";
            case STRUCT: return "struct";
            default: return super.toString().toLowerCase();
            }
        }

        public static SerializedType fromValue(int value) {
            for(SerializedType t: values()) {
                if(t.value == value) {
                    return t;
                }
            }
            throw new RuntimeException("Unknown value: " + value);
        }
    }

    enum OpndType {
        /** no immediate operands */
        None,
        /** immediate branch */
        Branch,
        /** uint64 constant */
        ImmUint64,
        /** index of local variable */
        IdxLocal,
        /** index of address */
        IdxAddress,
        /** index of byte array */
        IdxByteArray,
        /** index of string */
        IdxString,
        /** index of function handle */
        IdxFuncHandle,
        /** index of struct def */
        IdxStructDef,
        /** index of field def */
        IdxFieldDef
    }

    // 53 opcodes as of v1.0 (6/27/19)
    enum OpcodeDef {
        POP                        (0x01, 1, 0),
        RET                        (0x02,-1, 0),  // TODO: review 
        BR_TRUE                    (0x03, 1, 0, OpndType.Branch),
        BR_FALSE                   (0x04, 1, 0, OpndType.Branch),
        BRANCH                     (0x05, 0, 0, OpndType.Branch),
        LD_CONST                   (0x06, 0, 1, OpndType.ImmUint64),
        LD_ADDR                    (0x07, 0, 1, OpndType.IdxAddress),
        LD_STR                     (0x08, 0, 1, OpndType.IdxString),
        LD_TRUE                    (0x09, 0, 1),
        LD_FALSE                   (0x0A, 0, 1),
        COPY_LOC                   (0x0B, 0, 1, OpndType.IdxLocal, "Push the local identified by `LocalIndex` onto the stack. The value is copied and the local is still safe to use"),
        MOVE_LOC                   (0x0C, 0, 1, OpndType.IdxLocal, "Push the local identified by `LocalIndex` onto the stack. The local is moved and it is invalid to use from that point on, unless a store operation writes to the local before any read to that local"),
        ST_LOC                     (0x0D, 1, 0, OpndType.IdxLocal),
        LD_REF_LOC                 (0x0E, 0, 1, OpndType.IdxLocal),  // =BorrowLoc
        LD_REF_FIELD               (0x0F, 1, 1, OpndType.IdxFieldDef), // =BorrowField
        LD_BYTEARRAY               (0x10, 0, 1, OpndType.IdxByteArray),
        CALL                       (0x11,-1,-1, OpndType.IdxFuncHandle),
        PACK                       (0x12,-1, 1, OpndType.IdxStructDef),
        UNPACK                     (0x13, 1,-1, OpndType.IdxStructDef),
        READ_REF                   (0x14, 1, 1),
        WRITE_REF                  (0x15, 2, 0),
        ADD                        (0x16, 2, 1),
        SUB                        (0x17, 2, 1),
        MUL                        (0x18, 2, 1),
        MOD                        (0x19, 2, 1),
        DIV                        (0x1A, 2, 1),
        BIT_OR                     (0x1B, 2, 1),
        BIT_AND                    (0x1C, 2, 1),
        XOR                        (0x1D, 2, 1),  // also bitwise - libra's opcode naming is really not ideal here, but I assume that will be polished over the next months
        OR                         (0x1E, 2, 1),  // logical operations, all the way to GE 
        AND                        (0x1F, 2, 1),
        NOT                        (0x20, 1, 1),
        EQ                         (0x21, 2, 1),
        NEQ                        (0x22, 2, 1),
        LT                         (0x23, 2, 1),
        GT                         (0x24, 2, 1),
        LE                         (0x25, 2, 1),
        GE                         (0x26, 2, 1),
        ASSERT                     (0x27, 2, 0),
        GET_TXN_GAS_UNIT_PRICE     (0x28, 0, 1),
        GET_TXN_MAX_GAS_UNITS      (0x29, 0, 1),
        GET_GAS_REMAINING          (0x2A, 0, 1),
        GET_TXN_SENDER             (0x2B, 0, 1),  // =GetTxnSenderAddress
        EXISTS                     (0x2C, 1, 1, OpndType.IdxStructDef),
        BORROW_REF                 (0x2D, 1, 1, OpndType.IdxStructDef), // =BorrowGlobal
        RELEASE_REF                (0x2E, 1, 0),
        MOVE_FROM                  (0x2F, 1, 1, OpndType.IdxStructDef),
        MOVE_TO                    (0x30, 1, 0, OpndType.IdxStructDef),  //= MoveToSender
        CREATE_ACCOUNT             (0x31, 1, 0),
        EMIT_EVENT                 (0x32, 3, 0),
        GET_TXN_SEQUENCE_NUMBER    (0x33, 0, 1),
        GET_TXN_PUBLIC_KEY         (0x34, 0, 1),
        FREEZE_REF                 (0x35, 1, 1),
        ;

        int v;
        int popcnt;
        int pushcnt;
        OpndType opndtype;
        String docstr;

        private OpcodeDef(int v, int popcnt, int pushcnt) {
            this(v, popcnt, pushcnt, OpndType.None);
        }

        private OpcodeDef(int v, int popcnt, int pushcnt, OpndType opndtype) {
            this(v, popcnt, pushcnt, opndtype, null);
        }

        private OpcodeDef(int v, int popcnt, int pushcnt, String docstr) {
            this(v, popcnt, pushcnt, OpndType.None, docstr);
        }

        private OpcodeDef(int v, int popcnt, int pushcnt, OpndType opndtype, String docstr) {
            this.v = v;
            this.popcnt = popcnt;
            this.pushcnt = pushcnt;
            this.opndtype = opndtype;
            this.docstr = docstr;
        }

        public int getOpcode() {
            return v;
        }

        public int getPopCount() {
            return popcnt;
        }

        public int getPushCount() {
            return pushcnt;
        }

        public OpndType getOperandType() {
            return opndtype;
        }

        public String getDocString() {
            return docstr;
        }

        public static OpcodeDef fromValue(int opcode) {
            if(opcode < 1 || opcode > 0x35) {
                throw new RuntimeException("Unknown opcode: " + opcode);
            }
            OpcodeDef op = OpcodeDef.values()[opcode - 1];
            Assert.a(op.v == opcode);
            return op;
        }

        public String getLLMnemonic() {
            return toString();
        }

        public boolean isBinaryOperation() {
            switch(this) {
            case ADD:
            case SUB:
            case MUL:
            case MOD:
            case DIV:
            case BIT_OR:
            case BIT_AND:
            case XOR:
                return true;
            default:
                return false;
            }
        }

        public boolean isLogicalOperation() {
            switch(this) {
            case NOT:
            case OR:
            case AND:
            case EQ:
            case NEQ:
            case LT:
            case GT:
            case LE:
            case GE:
                return true;
            default:
                return false;
            }
        }

        public String getHLMnemonic() {
            switch(this) {
            case LD_REF_LOC:
                return "BorrowLoc";
            case LD_REF_FIELD:
                return "BorrowField";
            case BORROW_REF:
                return "BorrowGlobal";
            case MOVE_TO:
                return "MoveToSender";
            default:
                // convert to camel-case
                String s = "";
                for(String elt: getLLMnemonic().split("_")) {
                    s += elt.charAt(0) + elt.substring(1).toLowerCase();
                }
                return s;
            }
        }
    }

    //@formatter:on
}
