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

import java.util.List;

import com.pnf.libravm.Libra.BinaryType;
import com.pnfsoftware.jeb.core.units.INativeCodeUnit;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.IGlobalAnalyzer;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.INativeDecompilationTarget;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.INativeDecompilerUnit;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.NativeDecompilationStage;
import com.pnfsoftware.jeb.core.units.code.asm.items.INativeClassItem;
import com.pnfsoftware.jeb.core.units.code.asm.items.INativeMethodItem;
import com.pnfsoftware.jeb.core.units.code.asm.type.IClassManager;
import com.pnfsoftware.jeb.core.units.code.asm.type.IClassType;
import com.pnfsoftware.jeb.core.units.code.asm.type.ITypeManager;
import com.pnfsoftware.jeb.util.concurrent.ACLock;
import com.pnfsoftware.jeb.util.logging.GlobalLog;
import com.pnfsoftware.jeb.util.logging.ILogger;

/**
 * Simple libra module rebuilder. Will generate a class item.
 *
 * @author Nicolas Falliere
 *
 */
public class LibraModuleRebuilder implements IGlobalAnalyzer {
    private static final ILogger logger = GlobalLog.getLogger(LibraModuleRebuilder.class);

    public static final String StandardModuleName = "DecompiledModule";
    
    LibraUnit libra;
    INativeCodeUnit<LibraInstruction> code;
    INativeDecompilerUnit<LibraInstruction> decomp;

    private INativeClassItem classItem;

    @SuppressWarnings("unchecked")
    public LibraModuleRebuilder(INativeDecompilerUnit<LibraInstruction> decomp) {
        this.decomp = decomp;
        this.code = (INativeCodeUnit<LibraInstruction>)decomp.getParent();
        this.libra = (LibraUnit)code.getParent();
    }

    public INativeClassItem getRebuiltModule() {
        return classItem;
    }

    @Override
    public boolean perform() {
        // nothing to rebuild if we are dealing with a script (single main function)
        if(libra.getBinaryType() == BinaryType.SCRIPT) {
            return true;
        }

        // expect a module
        if(libra.getBinaryType() != BinaryType.MODULE) {
            return false;
        }

        if(classItem != null) {
            throw new IllegalStateException("The module was already rebuilt");
        }
        try(ACLock unused = code.getLock().a()) {
            rebuildModule();
        }
        return true;
    }

    void rebuildModule() {
        ITypeManager typeman = code.getTypeManager();
        IClassManager classman = code.getClassManager();

        // 1) decompile all routines
        List<? extends INativeMethodItem> routines = code.getInternalMethods();
        for(INativeMethodItem routine: routines) {
            decompile(routine, null);
        }

        // 2) create a class type (~ the module)
        String classname = StandardModuleName;
        IClassType classType = typeman.createClassType(classname, 1, 0);
        // create a class item (~ the class type implementation) - class items are displayed in the code hierarchy)
        classItem = classman.createClassItem(classType);
        typeman.completeClassTypeInitialization(classType);

        // 3) add all internal routines to the class item
        for(INativeMethodItem routine: routines) {
            classman.addNonVirtualMethod(classItem, routine);
        }
        classman.completeClassItemInitialization(classItem);

        // 4) move all methods items to the class items
        for(INativeMethodItem routine: routines) {
            classman.move(routine, classItem);
        }
    }

    INativeDecompilationTarget decompile(INativeMethodItem routine, NativeDecompilationStage stage) {
        try {
            return decomp.decompileMethod(routine, true, stage, null);
        }
        catch(Exception e) {
            logger.catchingSilent(e);
            return null;
        }
    }

    INativeDecompilationTarget decompilePartial(INativeMethodItem routine) {
        return decompile(routine, NativeDecompilationStage.LIFTING_COMPLETED);
    }
}
