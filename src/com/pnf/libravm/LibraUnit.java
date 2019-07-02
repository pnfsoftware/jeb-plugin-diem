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

import static com.pnf.libravm.Libra.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.pnfsoftware.jeb.client.Licensing;
import com.pnfsoftware.jeb.core.IUnitCreator;
import com.pnfsoftware.jeb.core.input.IInput;
import com.pnfsoftware.jeb.core.properties.IPropertyDefinitionManager;
import com.pnfsoftware.jeb.core.units.IUnit;
import com.pnfsoftware.jeb.core.units.IUnitProcessor;
import com.pnfsoftware.jeb.core.units.NotificationType;
import com.pnfsoftware.jeb.core.units.UnitNotification;
import com.pnfsoftware.jeb.core.units.code.asm.memory.IVirtualMemory;
import com.pnfsoftware.jeb.core.units.code.asm.memory.MemoryException;
import com.pnfsoftware.jeb.core.units.code.asm.processor.ProcessorException;
import com.pnfsoftware.jeb.core.units.codeobject.AbstractCodeObjectUnit;
import com.pnfsoftware.jeb.core.units.codeobject.CodeObjectUnitUtil;
import com.pnfsoftware.jeb.core.units.codeobject.ILoaderInformation;
import com.pnfsoftware.jeb.core.units.codeobject.ISegmentInformation;
import com.pnfsoftware.jeb.core.units.codeobject.ISymbolInformation;
import com.pnfsoftware.jeb.core.units.codeobject.LoaderInformation;
import com.pnfsoftware.jeb.core.units.codeobject.ProcessorType;
import com.pnfsoftware.jeb.core.units.codeobject.SegmentInformation;
import com.pnfsoftware.jeb.core.units.codeobject.SymbolInformation;
import com.pnfsoftware.jeb.core.units.codeobject.SymbolType;
import com.pnfsoftware.jeb.util.base.Assert;
import com.pnfsoftware.jeb.util.base.Throwables;
import com.pnfsoftware.jeb.util.format.TextBuilder;
import com.pnfsoftware.jeb.util.io.ByteArray;
import com.pnfsoftware.jeb.util.io.Endianness;
import com.pnfsoftware.jeb.util.io.IO;
import com.pnfsoftware.jeb.util.logging.GlobalLog;
import com.pnfsoftware.jeb.util.logging.ILogger;
import com.pnfsoftware.jeb.util.serialization.annotations.Ser;
import com.pnfsoftware.jeb.util.serialization.annotations.SerId;

/**
 * Parser for Libra modules and scripts. 
 *
 * @author Nicolas Falliere
 *
 */
@Ser
public class LibraUnit extends AbstractCodeObjectUnit {
    private static final ILogger logger = GlobalLog.getLogger(LibraUnit.class);

    static final int ptrsize = 64;
    static final int ptrsizeInBytes = ptrsize / 8;

    // arbitrary mapping addresses for Libra objects
    private static final long phyDataBase = 0L;
    private static final String segData = ".data";

    private static final long phyCodeBase = 0x1000_0000L;
    private static final String segCode = ".code";

    private static final long phyImportsBase = 0x2000_0000L;
    private static final String segImports = ".imports";

    @SerId(1)
    byte[] rawbytes;

    @SerId(2)
    LibraBytecodeParser bytecodeParser;

    @SerId(10)
    LibraPool<ModuleHandle> moduleHandles = new LibraPool<>("Module Handles");
    @SerId(11)
    LibraPool<StructHandle> structHandles = new LibraPool<>("Struct Handles");
    @SerId(12)
    LibraPool<FunctionHandle> functionHandles = new LibraPool<>("Function Handles");
    @SerId(13)
    LibraPool<AddressEntry> addressPool = new LibraPool<>("Addresses");
    @SerId(14)
    LibraPool<BytearrayEntry> bytearrayPool = new LibraPool<>("ByteArrays");
    @SerId(15)
    LibraPool<StringEntry> stringPool = new LibraPool<>("Strings");
    @SerId(16)
    LibraPool<TypeSignature> typeSignatures = new LibraPool<>("Type Signatures");
    @SerId(17)
    LibraPool<LocalSignature> localSignatures = new LibraPool<>("Local Signatures");
    @SerId(18)
    LibraPool<FunctionSignature> functionSignatures = new LibraPool<>("Function Signatures");
    @SerId(19)
    LibraPool<StructDef> structDefs = new LibraPool<>("Struct Definitions");  // for modules only
    @SerId(20)
    LibraPool<FieldDef> fieldDefs = new LibraPool<>("Field Definitions");  // for modules only
    @SerId(21)
    LibraPool<FunctionDef> functionDefs = new LibraPool<>("Function Definitions");  // for modules only
    @SerId(22)
    FunctionDef main;  // for scripts only

    public LibraUnit(String name, IInput input, IUnitProcessor unitProcessor, IUnitCreator parent,
            IPropertyDefinitionManager pdm) {
        super(input, LibraIdentifier.TYPE, name, unitProcessor, parent, pdm);
    }

    public LibraBytecodeParser getBytecodeParser() {
        return bytecodeParser;
    }

    @Override
    protected boolean processInternal() {
        bytecodeParser = new LibraBytecodeParser(this);

        try(InputStream in = getInput().getStream()) {
            rawbytes = IO.readInputStream(in);

            // skip the preamble, magic+version, as it was verified by the identifier
            ByteArray ba = new ByteArray(rawbytes, 10);

            // parse the tables
            int tablecount = ba.u8();
            for(int i = 0; i < tablecount; i++) {
                int _type = ba.u8();
                TableType t = TableType.fromValue(_type);

                int table_offset = ba.u31();
                int table_size = ba.u31();

                processTable(t, ba.copy(table_offset, table_offset + table_size));

                // one section for each table
                addSection(new SegmentInformation(t.toString(), table_offset, table_size, 0, 0,
                        ISegmentInformation.FLAG_READ));
            }

            // all is parsed, we can pretty-print tables safely
            if(Licensing.isDebugBuild()) {
                logger.i(formatTables());
            }

            // 1) create a pseudo DATA segment holding the addresses, bytearrays, and strings
            long currentAddress = phyDataBase;
            for(AbstractDataEntry e: getDataEntries()) {
                e.mappedAddress = currentAddress;
                e.mappedSize = e.getBytes().length;
                SymbolInformation symbol = new SymbolInformation(SymbolType.VARIABLE, 0,
                        e.getIndex(), null, 0, e.mappedAddress, e.mappedSize);
                String basetype = e instanceof StringEntry ? "char": "byte";
                symbol.setSymbolDataTypeInformation(basetype + "[" + e.mappedSize + "]");
                addSymbol(symbol);
                currentAddress += e.mappedSize;
            }
            int segsize = (int)(currentAddress - phyDataBase);
            if(segsize > 0) {
                addSegment(new SegmentInformation(segData, 0, 0, phyDataBase, segsize, ISegmentInformation.FLAG_RWX));
            }

            // 2) create a pseudo CODE segment holding the aggregate of all internal functions
            currentAddress = phyCodeBase;
            for(FunctionDef e: getInternalFunctions()) {
                e.mappedAddress = currentAddress;
                e.mappedSize = e.getCode().getInsnFileSize();
                addSymbol(new SymbolInformation(SymbolType.FUNCTION, ISymbolInformation.FLAG_FUNCTION_CODE_CONTIGUOUS,
                        e.getIndex(), e.getHandle(this).getName(this), 0, e.mappedAddress, e.mappedSize));
                currentAddress += e.mappedSize;
            }
            segsize = (int)(currentAddress - phyCodeBase);
            if(segsize > 0) {
                addSegment(new SegmentInformation(segCode, 0, 0, phyCodeBase, segsize, ISegmentInformation.FLAG_RWX));
            }

            // 3) create a pseudo IMPORTS segment holding pointers to functions residing in external modules
            currentAddress = phyImportsBase;
            // imported functions, represented as unknown pointers
            for(FunctionHandle e: getExternalFunctionHandles()) {
                e.mappedAddress = currentAddress;
                //e.mappedSize = Libra.getValueTypeSize(e.type.content_type);
                SymbolInformation symbol = new SymbolInformation(SymbolType.PTRFUNCTION,
                        ISymbolInformation.FLAG_IMPORTED, e.getIndex(), e.getName(this), 0, currentAddress,
                        ptrsizeInBytes);
                addSymbol(symbol);
                currentAddress += ptrsizeInBytes;
            }
            segsize = (int)(currentAddress - phyImportsBase);
            if(segsize > 0) {
                addSegment(new SegmentInformation(segImports, 0, 0, phyImportsBase, segsize,
                        ISegmentInformation.FLAG_RWX));
            }

            //@formatter:off
            LoaderInformation ldinfo = new LoaderInformation.Builder()
                    .setVersion("1.0")
                    .setTargetProcessor(ProcessorType.UNKNOWN)
                    .setWordSize(ptrsize)
                    .setEndianness(Endianness.LITTLE_ENDIAN)
                    .setImageBase(0)
                    .setImageSize(0x8000_0000L)
                    .setFlags(ILoaderInformation.FLAG_LIBRARY_FILE)
                    .setEntryPoint(phyCodeBase)
                    .build();
            //@formatter:on
            setLoaderInformation(ldinfo);

            // inform JEB generic parser to go ahead and process the bytecode
            try {
                String t = LibraDisassemblerPlugin.TYPE;
                IUnit img = getUnitProcessor().process(t + " image", getInput(), this, t, true);
                if(img != null) {
                    addChildUnit(img);
                }
            }
            catch(Exception e) {
                logger.catching(e);
                addNotification(new UnitNotification(NotificationType.UNSUPPORTED_FEATURE,
                        "The bytecode was not disassembled"));
            }

            // done
            return true;
        }
        catch(Exception e) {
            logger.catching(e);
            return false;
        }
    }

    private void processTable(TableType t, ByteArray ba) {
        switch(t) {
        case MODULE_HANDLES:
            loadModuleHandles(ba);
            break;
        case STRUCT_HANDLES:
            loadStructHandles(ba);
            break;
        case FUNCTION_HANDLES:
            loadFunctionHandles(ba);
            break;
        case ADDRESS_POOL:
            loadAddressPool(ba);
            break;
        case BYTE_ARRAY_POOL:
            loadBytearrayPool(ba);
            break;
        case STRING_POOL:
            loadStringPool(ba);
            break;
        case TYPE_SIGNATURES:
            loadTypeSignatures(ba);
            break;
        case LOCALS_SIGNATURES:
            loadLocalSignatures(ba);
            break;
        case FUNCTION_SIGNATURES:
            loadFunctionSignatures(ba);
            break;
        case STRUCT_DEFS:
            loadStructDefs(ba);
            break;
        case FIELD_DEFS:
            loadFieldDefs(ba);
            break;
        case FUNCTION_DEFS:
            // TODO: verify: parsing function defs requires access to the other tables
            // it seems that is the case (all other tables are inserted first), but I haven't read that explicitly in the specs
            loadFunctionDefs(ba);
            break;
        case MAIN:
            // same note as FUNCTION_DEFS
            loadMain(ba);
            break;
        default:
            throw new RuntimeException("Unknown table type: " + t);
        }
    }

    private void loadModuleHandles(ByteArray ba) {
        while(ba.position() < ba.maxPosition()) {
            int address = ba.varu16();
            int name = ba.varu16();
            moduleHandles.add(new ModuleHandle(address, name));
        }
    }

    private void loadStructHandles(ByteArray ba) {
        while(ba.position() < ba.maxPosition()) {
            int module_handle = ba.varu16();
            int name = ba.varu16();
            boolean is_resource = ba.u8() != 0;
            structHandles.add(new StructHandle(module_handle, name, is_resource));
        }
    }

    private void loadFunctionHandles(ByteArray ba) {
        while(ba.position() < ba.maxPosition()) {
            int module_handle = ba.varu16();
            int name = ba.varu16();
            int signature = ba.varu16();
            functionHandles.add(new FunctionHandle(module_handle, name, signature));
        }
    }

    private void loadAddressPool(ByteArray ba) {
        if(ba.available() % ADDRESS_LENGTH != 0) {
            throw new RuntimeException();
        }
        while(ba.position() < ba.maxPosition()) {
            byte[] a = ba.get(ADDRESS_LENGTH);
            addressPool.add(new AddressEntry(a));
        }
    }

    private void loadBytearrayPool(ByteArray ba) {
        while(ba.position() < ba.maxPosition()) {
            int len = ba.vari32();
            if(len < 0 || len > 0xFFFF) {
                throw new RuntimeException();
            }
            byte[] bytes = ba.get(len);
            bytearrayPool.add(new BytearrayEntry(bytes));
        }
    }

    private void loadStringPool(ByteArray ba) {
        while(ba.position() < ba.maxPosition()) {
            int len = ba.vari32();
            if(len < 0 || len > 0xFFFF) {
                throw new RuntimeException();
            }
            int off = ba.position();
            String s = new String(ba.bytes(), off, len);
            ba.position(off + len);
            stringPool.add(new StringEntry(s));
        }
    }

    private void loadFunctionSignatures(ByteArray ba) {
        while(ba.position() < ba.maxPosition()) {
            int st = ba.u8();
            if(st != SignatureType.FUNCTION_SIGNATURE.getValue()) {
                throw new RuntimeException("Expected a function signature type, got " + st);
            }

            int cnt = ba.u8();
            List<SignatureToken> returnTokens = new ArrayList<>(cnt);
            for(int i = 0; i < cnt; i++) {
                returnTokens.add(readSigToken(ba));
            }

            cnt = ba.u8();
            List<SignatureToken> paramTokens = new ArrayList<>(cnt);
            for(int i = 0; i < cnt; i++) {
                paramTokens.add(readSigToken(ba));
            }

            functionSignatures.add(new FunctionSignature(returnTokens, paramTokens));
        }
    }

    private void loadLocalSignatures(ByteArray ba) {
        while(ba.position() < ba.maxPosition()) {
            int st = ba.u8();
            if(st != SignatureType.LOCAL_SIGNATURE.getValue()) {
                throw new RuntimeException("Expected a local signature type, got " + st);
            }

            int cnt = ba.u8();
            List<SignatureToken> tokens = new ArrayList<>(cnt);
            for(int i = 0; i < cnt; i++) {
                tokens.add(readSigToken(ba));
            }

            localSignatures.add(new LocalSignature(tokens));
        }
    }

    private void loadTypeSignatures(ByteArray ba) {
        while(ba.position() < ba.maxPosition()) {
            int st = ba.u8();
            if(st != SignatureType.TYPE_SIGNATURE.getValue()) {
                throw new RuntimeException("Expected a type signature type, got " + st);
            }

            SignatureToken token = readSigToken(ba);
            typeSignatures.add(new TypeSignature(token));
        }
    }

    private SignatureToken readSigToken(ByteArray ba) {
        int value = ba.u8();
        SerializedType st = SerializedType.fromValue(value);
        switch(st) {
        case BOOL:
        case INTEGER:
        case STRING:
        case BYTEARRAY:
        case ADDRESS:
            return new SignatureToken(st);
        case REFERENCE:
            return new SignatureToken(readSigToken(ba), false);
        case MUTABLE_REFERENCE:
            return new SignatureToken(readSigToken(ba), true);
        case STRUCT:
            return new SignatureToken(ba.varu16());
        default:
            throw new RuntimeException("TBI: " + st);
        }
    }

    private void loadMain(ByteArray ba) {
        main = readFunctionDef(ba);
    }

    private void loadFunctionDefs(ByteArray ba) {
        while(ba.position() < ba.maxPosition()) {
            FunctionDef f = readFunctionDef(ba);
            functionDefs.add(f);
        }
    }

    private FunctionDef readFunctionDef(ByteArray ba) {
        int function_handle_index = ba.varu16();
        int flags = ba.u8();
        CodeUnit codeunit = readCodeUnit(ba, function_handle_index, flags);
        return new FunctionDef(function_handle_index, flags, codeunit);
    }

    private CodeUnit readCodeUnit(ByteArray ba, int function_handle_index, int flags) {
        int max_stack_size = ba.varu16();
        int locals_index = ba.varu16();
        int insncnt = ba.u16();

        int bytecode_offset = ba.position();
        logger.i("==> Parsing bytecode at 0x%X: fh=%d, mss=%d, sig=%d, insncnt=%d", bytecode_offset,
                function_handle_index, max_stack_size, locals_index, insncnt);

        List<LibraInstruction> insnlist;
        try {
            insnlist = bytecodeParser.parseFunction(function_handle_index, insncnt, bytecode_offset, ba.maxPosition());
        }
        catch(ProcessorException e) {
            throw new RuntimeException(e);
        }

        CodeUnit code = new CodeUnit(max_stack_size, locals_index, insnlist);
        code.setInsnFileOffset(bytecode_offset);

        // move the cursor forward (= do as if reading was done from this bytearray)
        ba.skip(code.getInsnFileSize());

        return code;
    }

    private void loadStructDefs(ByteArray ba) {
        while(ba.position() < ba.maxPosition()) {
            int struct_handle = ba.varu16();
            int field_count = ba.varu16();
            int fields = ba.varu16();
            structDefs.add(new StructDef(struct_handle, field_count, fields));
        }
    }

    private void loadFieldDefs(ByteArray ba) {
        while(ba.position() < ba.maxPosition()) {
            int struct_handle = ba.varu16();
            int name = ba.varu16();
            int signature = ba.varu16();
            fieldDefs.add(new FieldDef(struct_handle, name, signature));
        }
    }

    // --- end of pool parsing methods ---

    /** order: addresses, bytearrays, strings */
    public List<AbstractDataEntry> getDataEntries() {
        List<AbstractDataEntry> r = new ArrayList<>();
        r.addAll(addressPool.getAll());
        r.addAll(bytearrayPool.getAll());
        r.addAll(stringPool.getAll());
        return r;
    }

    public List<FunctionDef> getInternalFunctions() {
        if(main != null) {
            // TODO: could be done before after processing the tables 
            Assert.a(functionDefs.isEmpty(), "Libra script cannot contain functions defs aside from main()");
            return Arrays.asList(main);
        }
        return functionDefs.getAll();
    }

    public List<FunctionHandle> getExternalFunctionHandles() {
        List<FunctionHandle> internals = new ArrayList<>();
        for(FunctionDef e: getInternalFunctions()) {
            internals.add(e.getHandle(this));
        }
        List<FunctionHandle> r = new ArrayList<>();
        for(FunctionHandle e: functionHandles) {
            // NOTE: we could check that the module name is not '<SELF>', but is this standard?
            if(!internals.contains(e)) {
                r.add(e);
            }
        }
        return r;
    }

    @Override
    protected boolean shouldAllocateFullImage() {
        return false;
    }

    @Override
    protected boolean applyRelocations(IVirtualMemory mem, long base) {
        // no relocation
        return false;
    }

    @Override
    protected boolean mapRawNoReloc(IVirtualMemory mem, long base) {
        if(base != 0) {
            throw new IllegalArgumentException("Must map at address 0");
        }

        // memory allocation
        super.mapRawNoReloc(mem, base);

        // write bytes
        try {
            // (segment .data) map addresses/bytearrays/strings
            if(CodeObjectUnitUtil.findSegmentByName(this, segData) != null) {
                for(AbstractDataEntry e: getDataEntries()) {
                    int size = e.mappedSize;
                    int writesize = mem.write(e.mappedAddress, size, e.getBytes(), 0);
                    if(writesize != size) {
                        throw new MemoryException("Partial write");
                    }
                }
            }

            // (segment .code) map internal functions
            if(CodeObjectUnitUtil.findSegmentByName(this, segCode) != null) {
                for(FunctionDef e: getInternalFunctions()) {
                    int size = e.getCode().getInsnFileSize();
                    int writesize = mem.write(e.mappedAddress, size, rawbytes, e.getCode().getInsnFileOffset());
                    if(writesize != size) {
                        throw new MemoryException("Partial write");
                    }
                }
            }

            // (segment .imports) external function pointers
            if(CodeObjectUnitUtil.findSegmentByName(this, segImports) != null) {
                for(@SuppressWarnings("unused")
                FunctionHandle e: getExternalFunctionHandles()) {
                    // pass
                }
            }

            return true;
        }
        catch(MemoryException e) {
            throw new RuntimeException(e);
        }
    }

    BinaryType getBinaryType() {
        return main != null ? BinaryType.SCRIPT: BinaryType.MODULE;
    }

    int getStructFieldCount(int sd_index) {
        return structDefs.get(sd_index).getFieldCount();
    }

    FunctionSignature getFunctionSignature(int fh_index) {
        return functionHandles.get(fh_index).getSignature(this);
    }

    String getFunctionName(int fh_index) {
        return functionHandles.get(fh_index).getName(this);
    }

    FunctionDef getFunctionByAddress(long address) {
        for(FunctionDef e: getInternalFunctions()) {
            if(address == e.mappedAddress) {
                return e;
            }
        }
        return null;
    }

    FunctionDef getFunctionByName(String name) {
        if(name == null) {
            throw new IllegalArgumentException();
        }
        for(FunctionDef e: getInternalFunctions()) {
            FunctionHandle handle = e.getHandle(this);
            if(name.equals(handle.getName(this))) {
                return e;
            }
        }
        return null;
    }

    FunctionHandle getFunctionHandleByName(String name) {
        if(name == null) {
            throw new IllegalArgumentException();
        }
        for(FunctionHandle e: functionHandles) {
            if(name.equals(e.getName(this))) {
                return e;
            }
        }
        return null;
    }

    public String formatTables() {
        TextBuilder t = new TextBuilder();
        moduleHandles.format(this, t).eol();
        structHandles.format(this, t).eol();
        functionHandles.format(this, t).eol();
        structDefs.format(this, t).eol();
        fieldDefs.format(this, t).eol();
        typeSignatures.format(this, t).eol();
        functionSignatures.format(this, t).eol();
        localSignatures.format(this, t).eol();
        stringPool.format(this, t).eol();
        bytearrayPool.format(this, t).eol();
        addressPool.format(this, t).eol();
        functionDefs.format(this, t).eol();
        if(main != null) {
            main.format(this, t).eol();
        }
        return t.toString();
    }

    String formatObject(LibraObject o) {
        TextBuilder t = new TextBuilder();
        return o.format(this, t).toString();
    }

    @Override
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        //sb.append(super.getDescription());
        try {
            //sb.append("\n\nLibra tables:\n");
            sb.append(formatTables());
        }
        catch(Exception e) {
            sb.append("\n\nAn error occurred when formatting the tables:\n");
            sb.append(Throwables.formatStacktrace(e));
        }
        return sb.toString();
    }

    @Override
    public byte[] getIconData() {
        try {
            return IO.readInputStream(getClass().getResourceAsStream("libra_icon.png"));
        }
        catch(IOException e) {
            return null;
        }
    }
}
