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

import com.pnfsoftware.jeb.core.units.code.asm.analyzer.AbstractAnalyzerExtension;
import com.pnfsoftware.jeb.core.units.code.asm.type.IPrimitiveTypeManager;
import com.pnfsoftware.jeb.core.units.code.asm.type.ITypeManager;
import com.pnfsoftware.jeb.core.units.code.asm.type.PrimitiveCategory;
import com.pnfsoftware.jeb.util.serialization.annotations.Ser;

/**
 * Code analyzer extension. This extension is used by JEB's disassembler to customize the analysis.
 * Currently, it is used to register specific {@code Move} types not present in common typelibs.
 *
 * @author Nicolas Falliere
 *
 */
@Ser
public class DiemAnalyzerExtension extends AbstractAnalyzerExtension<DiemInstruction> {

    /** Register diem specific types */
    @Override
    public void typeManagerInitialized(ITypeManager typeman) {

        // Note the design choice here: 32-byte address type is kept as a primitive (along with uint64 and bool, of course)
        // the other types will be managed via pointers (bytearray, string, struct, references)

        IPrimitiveTypeManager pman = typeman.getPrimitives();
        pman.addPrimitive("bool", 8, PrimitiveCategory.UNSIGNED);
        pman.addPrimitive("u64", 8, PrimitiveCategory.UNSIGNED);
        pman.addPrimitive("address", 32, PrimitiveCategory.UNSIGNED);

        typeman.createAlias("byte", typeman.getType("unsigned char"));
        typeman.createAlias("bytearray", typeman.getType("byte*"));
        typeman.createAlias("string", typeman.getType("byte*"));
    }
}
