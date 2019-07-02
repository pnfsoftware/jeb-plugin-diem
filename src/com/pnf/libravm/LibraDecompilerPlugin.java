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
import com.pnfsoftware.jeb.core.PluginInformation;
import com.pnfsoftware.jeb.core.properties.IPropertyDefinitionManager;
import com.pnfsoftware.jeb.core.properties.impl.PropertyTypeBoolean;
import com.pnfsoftware.jeb.core.units.INativeCodeUnit;
import com.pnfsoftware.jeb.core.units.WellKnownUnitTypes;
import com.pnfsoftware.jeb.core.units.code.asm.AbstractNativeDecompilerPlugin;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.IEConverter;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.IGlobalAnalyzer;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.INativeDecompilerExtension;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.INativeDecompilerUnit;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ISourceCustomizer;

/**
 * Libra public plugin #3/3: decompiler.
 *
 * @author Nicolas Falliere
 *
 */
public class LibraDecompilerPlugin extends AbstractNativeDecompilerPlugin<LibraInstruction> {
    public static final String TYPE = WellKnownUnitTypes.pfxTypeDecompiler + LibraDisassemblerPlugin.TYPE;

    public LibraDecompilerPlugin() {
        super(TYPE, 0);
    }

    @Override
    public IPluginInformation getPluginInformation() {
        return new PluginInformation("Libra Decompiler", "Libra bytecode decompiler", "PNF Software",
                LibraIdentifier.VERSION);
    }

    @Override
    public void setupCustomProperties(IPropertyDefinitionManager pdm) {
        // FIXME: temporarily disabling until the friendly-naming issue with special vars is resolved
        pdm.addDefinition(propnameUseFriendlyVariableNames, PropertyTypeBoolean.create(false), null);
    }

    @Override
    public IEConverter<LibraInstruction> getConverter(INativeCodeUnit<LibraInstruction> unit) {
        LibraUnit libra = (LibraUnit)unit.getParent();
        return new LibraConverter(libra, unit);
    }

    @Override
    public INativeDecompilerExtension getPrimaryExtension(INativeDecompilerUnit<LibraInstruction> decompiler) {
        return new LibraDecompilerExtension();
    }

    @Override
    public IGlobalAnalyzer getGlobalAnalyzer(INativeDecompilerUnit<LibraInstruction> decompiler) {
        return new LibraModuleRebuilder(decompiler);
    }
    
    @Override
    public ISourceCustomizer getSourceCustomizer(INativeDecompilerUnit<LibraInstruction> decompiler) {
        return new LibraSourceCustomizer(decompiler);
    }
}
