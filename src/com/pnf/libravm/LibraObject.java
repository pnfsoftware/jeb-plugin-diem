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

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.pnf.libravm.Libra.SerializedType;
import com.pnfsoftware.jeb.util.format.Formatter;
import com.pnfsoftware.jeb.util.format.TextBuilder;
import com.pnfsoftware.jeb.util.serialization.annotations.Ser;
import com.pnfsoftware.jeb.util.serialization.annotations.SerId;

/**
 * Generic interface for Libra objects.
 *
 * @author Nicolas Falliere
 *
 */
public interface LibraObject {

    /**
     * Format the Libra object.
     * 
     * @param l libra unit
     * @param t a buffer for output storage
     * @return the parameter t to allow call-chaining
     */
    TextBuilder format(LibraUnit l, TextBuilder t);
}

/**
 * Super type for {@code address}, {@code bytearray}, and {@code string} types.
 */
abstract class AbstractDataEntry extends LibraPoolEntry {

    abstract byte[] getBytes();
}

@Ser
class ModuleHandle extends LibraPoolEntry {
    @SerId(1)
    private int address_index;
    @SerId(2)
    private int name_index;  // String

    public ModuleHandle(int address_index, int name_index) {
        this.address_index = address_index;
        this.name_index = name_index;
    }

    public AddressEntry getAddress(LibraUnit l) {
        return l.addressPool.get(address_index);
    }

    public String getName(LibraUnit l) {
        return l.stringPool.get(name_index).get();
    }

    public String getFullName(LibraUnit l) {
        return l.addressPool.get(address_index) + "." + l.stringPool.get(name_index).get();
    }

    @Override
    public TextBuilder format(LibraUnit l, TextBuilder t) {
        l.addressPool.get(address_index).format(l, t);
        t.append(".");
        l.stringPool.get(name_index).format(l, t);
        return t;
    }
}

@Ser
class StructHandle extends LibraPoolEntry {
    @SerId(1)
    private int modulehandle_index;  // ModuleHandle
    @SerId(2)
    private int name_index;  // String
    @SerId(3)
    private boolean is_resource;

    public StructHandle(int modulehandle_index, int name_index, boolean is_resource) {
        this.modulehandle_index = modulehandle_index;
        this.name_index = name_index;
        this.is_resource = is_resource;
    }

    public boolean isResource() {
        return is_resource;
    }

    public ModuleHandle getModule(LibraUnit l) {
        return l.moduleHandles.get(modulehandle_index);
    }

    public String getName(LibraUnit l) {
        return l.stringPool.get(name_index).get();
    }

    public String getFullName(LibraUnit l) {
        String s = l.stringPool.get(name_index).get();
        s += "@";
        s += l.moduleHandles.get(modulehandle_index).getFullName(l);
        return s;
    }

    @Override
    public TextBuilder format(LibraUnit l, TextBuilder t) {
        if(is_resource) {
            t.append("resource ");
        }
        l.stringPool.get(name_index).format(l, t);
        t.append("@");
        l.moduleHandles.get(modulehandle_index).format(l, t);
        return t;
    }
}

@Ser
class FunctionHandle extends LibraPoolEntry {
    @SerId(1)
    private int modulehandle_index;  // ModuleHandle
    @SerId(2)
    private int name_index;  // String
    @SerId(3)
    private int signature_index;

    public FunctionHandle(int modulehandle_index, int name_index, int signature_index) {
        this.modulehandle_index = modulehandle_index;
        this.name_index = name_index;
        this.signature_index = signature_index;
    }

    public ModuleHandle getModule(LibraUnit l) {
        return l.moduleHandles.get(modulehandle_index);
    }

    public String getName(LibraUnit l) {
        //return l.stringPool.get(name_index).get();
        return getFullName(l);
    }

    private String getFullName(LibraUnit l) {
        String fname = l.stringPool.get(name_index).get();
        String modname = l.moduleHandles.get(modulehandle_index).getName(l);
        if("<self>".equalsIgnoreCase(modname)) {
            return fname;
        }
        return modname + "_" + fname;
    }

    public FunctionSignature getSignature(LibraUnit l) {
        return l.functionSignatures.get(signature_index);
    }

    @Override
    public TextBuilder format(LibraUnit l, TextBuilder t) {
        l.moduleHandles.get(modulehandle_index).format(l, t);
        t.append(".");
        l.stringPool.get(name_index).format(l, t);
        l.functionSignatures.get(signature_index).format(l, t);
        return t;
    }
}

@Ser
class AddressEntry extends AbstractDataEntry {
    @SerId(1)
    private byte[] bytes;

    public AddressEntry(byte[] bytes) {
        this.bytes = bytes;
    }

    @Override
    public byte[] getBytes() {
        return bytes;
    }

    @Override
    public String toString() {
        return "0x" + new BigInteger(bytes).toString(16).toUpperCase();
    }
}

@Ser
class BytearrayEntry extends AbstractDataEntry {
    @SerId(1)
    private byte[] bytes;

    public BytearrayEntry(byte[] bytes) {
        this.bytes = bytes;
    }

    @Override
    public byte[] getBytes() {
        return bytes;
    }

    @Override
    public String toString() {
        return Formatter.byteArrayToHexString(bytes);
    }
}

@Ser
class StringEntry extends AbstractDataEntry {
    @SerId(1)
    private String s;

    public StringEntry(String s) {
        if(s == null) {
            throw new IllegalArgumentException();
        }
        this.s = s;
    }

    /** UTF-8 encoded, null-terminated */
    @Override
    public byte[] getBytes() {
        try {
            byte[] a = s.getBytes("UTF-8");
            return Arrays.copyOf(a, a.length + 1);
        }
        catch(UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public String get() {
        return s;
    }

    @Override
    public String toString() {
        return s;
    }
}

@Ser
class TypeSignature extends LibraPoolEntry {
    @SerId(1)
    private SignatureToken token;

    public TypeSignature(SignatureToken token) {
        if(token == null) {
            throw new IllegalArgumentException();
        }
        this.token = token;
    }

    public SignatureToken getToken() {
        return token;
    }

    @Override
    public TextBuilder format(LibraUnit l, TextBuilder t) {
        return token.format(l, t);
    }
}

@Ser
class FunctionSignature extends LibraPoolEntry {
    @SerId(1)
    private List<SignatureToken> returnTokens;
    @SerId(2)
    private List<SignatureToken> paramTokens;

    public FunctionSignature(List<SignatureToken> returnTokens, List<SignatureToken> paramTokens) {
        this.returnTokens = returnTokens;
        this.paramTokens = paramTokens;
    }

    public List<SignatureToken> getReturnTokens() {
        return returnTokens;
    }

    public List<SignatureToken> getParamTokens() {
        return paramTokens;
    }

    @Override
    public String toString() {
        return paramTokens.toString() + ": " + returnTokens.toString();
    }

    @Override
    public TextBuilder format(LibraUnit l, TextBuilder t) {
        t.append("(");
        int i = 0;
        for(SignatureToken token: paramTokens) {
            if(i > 0) {
                t.append(", ");
            }
            token.format(l, t);
            i++;
        }
        t.append("): (");
        i = 0;
        for(SignatureToken token: returnTokens) {
            if(i > 0) {
                t.append(", ");
            }
            token.format(l, t);
            i++;
        }
        t.append(")");
        return t;
    }
}

@Ser
class LocalSignature extends LibraPoolEntry {
    @SerId(1)
    private List<SignatureToken> tokens;

    public LocalSignature(List<SignatureToken> tokens) {
        this.tokens = tokens;
    }

    public List<SignatureToken> getTokens() {
        return tokens;
    }

    @Override
    public String toString() {
        return tokens.toString();
    }

    @Override
    public TextBuilder format(LibraUnit l, TextBuilder t) {
        int i = 0;
        for(SignatureToken token: tokens) {
            if(i > 0) {
                t.append(", ");
            }
            token.format(l, t);
            i++;
        }
        return t;
    }
}

@Ser
class SignatureToken implements LibraObject {
    public static final SignatureToken stUint64 = new SignatureToken(SerializedType.INTEGER);
    public static final SignatureToken stBool = new SignatureToken(SerializedType.BOOL);
    public static final SignatureToken stAddress = new SignatureToken(SerializedType.ADDRESS);
    public static final SignatureToken stBytearray = new SignatureToken(SerializedType.BYTEARRAY);
    public static final SignatureToken stString = new SignatureToken(SerializedType.STRING);
    public static final SignatureToken stAnyImmutableRef = new SignatureToken(SerializedType.REFERENCE);
    public static final SignatureToken stAnyMutableRef = new SignatureToken(SerializedType.MUTABLE_REFERENCE);

    // common
    @SerId(1)
    private SerializedType st;
    // for REFERENCE, MUTABLE_REFERENCE
    @SerId(2)
    private SignatureToken ref;
    // for STRUCT
    @SerId(3)
    private Integer sh_index;

    /**
     * Create a simple (non struct, non ref) token type.
     */
    public SignatureToken(SerializedType st) {
        this.st = st;
    }

    /**
     * Create a {@code reference} ({@link SerializedType#MUTABLE_REFERENCE} or
     * {@link SerializedType#REFERENCE}) token type.
     */
    public SignatureToken(SignatureToken ref, boolean mutable) {
        this.st = mutable ? SerializedType.MUTABLE_REFERENCE: SerializedType.REFERENCE;
        if(ref == null) {
            throw new IllegalArgumentException();
        }
        this.ref = ref;
    }

    /**
     * Create a {@link SerializedType#STRUCT} token type.
     */
    public SignatureToken(int sh_index) {
        this.st = SerializedType.STRUCT;
        if(sh_index < 0) {
            throw new IllegalArgumentException();
        }
        this.sh_index = sh_index;
    }

    public SerializedType getSerializedType() {
        return st;
    }

    public SignatureToken getReference() {
        return ref;
    }

    public StructHandle getStructureHandle(LibraUnit l) {
        if(sh_index == null) {
            return null;
        }
        return l.structHandles.get(sh_index);
    }

    @Override
    public String toString() {
        String s = st.toString();
        if(ref != null) {
            s += ":" + ref;
        }
        else if(sh_index != null) {
            s += "(" + sh_index + ")";
        }
        return s;
    }

    @Override
    public TextBuilder format(LibraUnit l, TextBuilder t) {
        t.append(st);
        if(ref != null) {
            t.append(" ");
            ref.format(l, t);
        }
        else if(sh_index != null) {
            t.append(" ");
            l.structHandles.get(sh_index).format(l, t);
        }
        return t;
    }
}

@Ser
class FunctionDef extends LibraPoolEntry {
    @SerId(1)
    private int function_handle_index;
    /** flags: {@link Libra#PUBLIC}, {@link Libra#NATIVE} */
    @SerId(2)
    private int flags;
    @SerId(3)
    private CodeUnit code;

    public FunctionDef(int function_handle_index, int flags, CodeUnit code) {
        this.function_handle_index = function_handle_index;
        this.flags = flags;
        this.code = code;
    }

    public FunctionHandle getHandle(LibraUnit l) {
        return l.functionHandles.get(function_handle_index);
    }

    public String getName(LibraUnit l) {
        return getHandle(l).getName(l);
    }

    public CodeUnit getCode() {
        return code;
    }

    public int getFlags() {
        return flags;
    }

    @Override
    public TextBuilder format(LibraUnit l, TextBuilder t) {
        if(flags != 0) {
            t.append(Libra.formatFunctionFlags(flags)).append(" ");
        }
        l.functionHandles.get(function_handle_index).format(l, t);
        t.eol();
        t.indent();
        code.format(l, t);
        return t.unindent();
    }
}

@Ser
class CodeUnit implements LibraObject {
    @SerId(1)
    private int max_stack_size;
    @SerId(2)
    private int local_sig_index;
    @SerId(3)
    private List<LibraInstruction> insnlist;

    @SerId(4)
    private int bytecode_offset;

    public CodeUnit(int max_stack_size, int local_sig_index, List<LibraInstruction> insnlist) {
        this.max_stack_size = max_stack_size;
        this.local_sig_index = local_sig_index;
        this.insnlist = insnlist;
    }

    public int getMaxStackSize() {
        return max_stack_size;
    }

    public LocalSignature getLocals(LibraUnit l) {
        return l.localSignatures.get(local_sig_index);
    }

    public List<LibraInstruction> getInstructions() {
        return insnlist;
    }

    /** instructions size in bytes */
    public int getInsnFileSize() {
        int size = 0;
        for(LibraInstruction insn: insnlist) {
            size += insn.getSize();
        }
        return size;
    }

    public int getInsnFileOffset() {
        return bytecode_offset;
    }

    public void setInsnFileOffset(int offset) {
        bytecode_offset = offset;
    }

    @Override
    public TextBuilder format(LibraUnit l, TextBuilder t) {
        t.append("max_stack_size: ").append(max_stack_size).eol();
        t.append("locals: ");
        l.localSignatures.get(local_sig_index).format(l, t).eol();
        int i = 0;
        for(LibraInstruction insn: insnlist) {
            t.append(insn.format((long)i)).eol();
            i++;
        }
        return t;
    }
}

@Ser
class StructDef extends LibraPoolEntry {
    @SerId(1)
    private int structhandle_index;
    @SerId(2)
    private int field_count;
    /** starting index (see file_format.rs) */
    @SerId(3)
    private int fields_index;

    public StructDef(int structhandle_index, int field_count, int fields_index) {
        this.structhandle_index = structhandle_index;
        this.field_count = field_count;
        this.fields_index = fields_index;
    }

    public int getHandleIndex() {
        return structhandle_index;
    }

    public StructHandle getHandle(LibraUnit l) {
        return l.structHandles.get(structhandle_index);
    }

    public String getName(LibraUnit l) {
        return getHandle(l).getName(l);
    }

    public int getFieldCount() {
        return field_count;
    }

    public List<FieldDef> getFields(LibraUnit l) {
        List<FieldDef> r = new ArrayList<>(field_count);
        for(int i = 0; i < field_count; i++) {
            r.add(l.fieldDefs.get(fields_index + i));
        }
        return r;
    }

    @Override
    public TextBuilder format(LibraUnit l, TextBuilder t) {
        l.structHandles.get(structhandle_index).format(l, t);
        t.append(" { ");
        for(int i = 0; i < field_count; i++) {
            if(i > 0) {
                t.append(", ");
            }
            l.fieldDefs.get(fields_index + i).format(l, t);
        }
        t.append(" }");
        return t;
    }
}

@Ser
class FieldDef extends LibraPoolEntry {
    /**
     * a back-reference to the structure using that field - so we have a 1-on-1 correspondence here
     */
    @SerId(1)
    private int structhandle_index;
    @SerId(2)
    int name_index;  // StringIndex
    @SerId(3)
    private int signature_index;  // TypeSignatureIndex

    public FieldDef(int structhandle_index, int name_index, int signature_index) {
        this.structhandle_index = structhandle_index;
        this.name_index = name_index;
        this.signature_index = signature_index;
    }

    public StructHandle getStructureHandle(LibraUnit l) {
        return l.structHandles.get(structhandle_index);
    }

    public String getName(LibraUnit l) {
        return l.stringPool.get(name_index).get();
    }

    public TypeSignature getSignature(LibraUnit l) {
        return l.typeSignatures.get(signature_index);
    }

    @Override
    public TextBuilder format(LibraUnit l, TextBuilder t) {
        l.stringPool.get(name_index).format(l, t);
        t.append(": ");
        l.typeSignatures.get(signature_index).format(l, t);
        return t;
    }
}
