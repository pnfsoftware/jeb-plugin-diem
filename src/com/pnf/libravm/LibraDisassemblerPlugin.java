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

import com.pnfsoftware.jeb.core.IPluginInformation;
import com.pnfsoftware.jeb.core.IUnitCreator;
import com.pnfsoftware.jeb.core.PluginInformation;
import com.pnfsoftware.jeb.core.units.code.asm.AbstractNativeDisassemblerPlugin;
import com.pnfsoftware.jeb.core.units.code.asm.analyzer.INativeCodeAnalyzerExtension;
import com.pnfsoftware.jeb.core.units.code.asm.memory.IVirtualMemory;
import com.pnfsoftware.jeb.core.units.code.asm.memory.VirtualMemoryUtil;
import com.pnfsoftware.jeb.core.units.code.asm.processor.IProcessor;
import com.pnfsoftware.jeb.core.units.code.asm.render.GenericCodeFormatter;
import com.pnfsoftware.jeb.util.io.Endianness;

/**
 * Libra public plugin #2/3: disassembler.
 * 
 * @author Nicolas Falliere
 *
 */
public class LibraDisassemblerPlugin extends AbstractNativeDisassemblerPlugin<LibraInstruction> {
    public static final String TYPE = LibraIdentifier.TYPE + "_bc";

    public LibraDisassemblerPlugin() {
        super(TYPE, 0);
    }

    @Override
    public IPluginInformation getPluginInformation() {
        return new PluginInformation("Libra Disassembler", "Libra bytecode (Move) disassembler", "PNF Software",
                LibraIdentifier.VERSION);
    }

    @Override
    public boolean canBeProcessedOutsideCodeObject() {
        // parsing libra code outside a libra module would not make any sense
        return false;
    }

    @Override
    public IProcessor<LibraInstruction> getProcessor(IUnitCreator parent) {
        if(parent instanceof LibraUnit) {
            return ((LibraUnit)parent).getBytecodeParser();
        }
        return new LibraBytecodeParser();
    }

    @Override
    public IVirtualMemory getMemory(IUnitCreator parent) {
        // provide a standard 64-bit VM, we will map Libra objects onto it
        return VirtualMemoryUtil.createMemory(LibraUnit.ptrsize, 12, Endianness.LITTLE_ENDIAN);
    }

    @Override
    public GenericCodeFormatter<LibraInstruction> getCodeFormatter() {
        // custom disassembly formatting
        return new LibraCodeFormatter();
    }

    @Override
    public INativeCodeAnalyzerExtension<LibraInstruction> getAnalyzerExtension() {
        return new LibraAnalyzerExtension();
    }
}
