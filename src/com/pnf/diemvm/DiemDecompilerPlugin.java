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
 * Diem public plugin #3/3: decompiler.
 *
 * @author Nicolas Falliere
 *
 */
public class DiemDecompilerPlugin extends AbstractNativeDecompilerPlugin<DiemInstruction> {
    public static final String TYPE = WellKnownUnitTypes.pfxTypeDecompiler + DiemDisassemblerPlugin.TYPE;

    public DiemDecompilerPlugin() {
        super(TYPE, 0);
    }

    @Override
    public IPluginInformation getPluginInformation() {
        return new PluginInformation("Diem Decompiler", "Diem bytecode decompiler", "PNF Software",
                DiemIdentifier.VERSION);
    }

    @Override
    public void setupCustomProperties(IPropertyDefinitionManager pdm) {
        // FIXME: temporarily disabling until the friendly-naming issue with special vars is resolved
        pdm.addDefinition(propnameUseFriendlyVariableNames, PropertyTypeBoolean.create(false), null);
    }

    @Override
    public IEConverter<DiemInstruction> getConverter(INativeCodeUnit<DiemInstruction> unit) {
        DiemUnit diem = (DiemUnit)unit.getParent();
        return new DiemConverter(diem, unit);
    }

    @Override
    public INativeDecompilerExtension getPrimaryExtension(INativeDecompilerUnit<DiemInstruction> decompiler) {
        return new DiemDecompilerExtension();
    }

    @Override
    public IGlobalAnalyzer getGlobalAnalyzer(INativeDecompilerUnit<DiemInstruction> decompiler) {
        return new DiemModuleRebuilder(decompiler);
    }
    
    @Override
    public ISourceCustomizer getSourceCustomizer(INativeDecompilerUnit<DiemInstruction> decompiler) {
        return new DiemSourceCustomizer(decompiler);
    }
}
