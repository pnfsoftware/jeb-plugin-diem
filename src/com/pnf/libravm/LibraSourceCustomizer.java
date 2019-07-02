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

import com.pnf.libravm.Libra.BinaryType;
import com.pnfsoftware.jeb.core.units.INativeCodeUnit;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.INativeDecompilerUnit;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.SourceCustomizerAdapter;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ast.COutputSink;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ast.ICClass;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ast.ICMethod;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ast.ICNativeStatement;
import com.pnfsoftware.jeb.core.units.code.asm.items.INativeMethodItem;

/**
 * Customize decompiled source code rendering to make it look more Move friendly.
 *
 * @author Nicolas Falliere
 *
 */
public class LibraSourceCustomizer extends SourceCustomizerAdapter {
    LibraUnit libra;
    INativeCodeUnit<LibraInstruction> code;
    INativeDecompilerUnit<LibraInstruction> decomp;

    @SuppressWarnings("unchecked")
    public LibraSourceCustomizer(INativeDecompilerUnit<LibraInstruction> decomp) {
        this.decomp = decomp;
        this.code = (INativeCodeUnit<LibraInstruction>)decomp.getParent();
        this.libra = (LibraUnit)code.getParent();
    }

    @Override
    public boolean generateClassDeclarationLine(ICClass elt, COutputSink out) {
        if(libra.getBinaryType() == BinaryType.MODULE) {
            out.appendKeyword("module");
            out.space();
            elt.getClasstype().generate(out);
            return true;
        }
        return false;
    }

    @Override
    public boolean preFieldsGeneration(ICClass elt, COutputSink out) {
        // short-circuit traditional C++
        return true;
    }

    @Override
    public boolean preMethodsGeneration(ICClass elt, COutputSink out) {
        // short-circuit traditional C++
        return true;
    }

    @Override
    public boolean generateMethodDeclarationLine(ICMethod elt, COutputSink out) {
        int index = elt.getIndex();
        INativeMethodItem routine = code.getMethodByIndex(index);
        FunctionDef f = libra.getFunctionByAddress(routine.getData().getMemoryAddress());
        if(f.getFlags() != 0) {
            out.appendKeyword(Libra.formatFunctionFlags(f.getFlags()));
            out.space();
        }
        // let the default generator generate the rest
        return false;
    }
    
    @Override
    public boolean generateNativeStatement(ICNativeStatement elt, COutputSink out) {
        // TODO Auto-generated method stub
        return super.generateNativeStatement(elt, out);
    }
}
