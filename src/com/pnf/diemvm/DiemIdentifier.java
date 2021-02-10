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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;

import com.pnfsoftware.jeb.core.IPluginInformation;
import com.pnfsoftware.jeb.core.IUnitCreator;
import com.pnfsoftware.jeb.core.PluginInformation;
import com.pnfsoftware.jeb.core.Version;
import com.pnfsoftware.jeb.core.input.IInput;
import com.pnfsoftware.jeb.core.units.AbstractUnitIdentifier;
import com.pnfsoftware.jeb.core.units.IUnit;
import com.pnfsoftware.jeb.core.units.IUnitProcessor;
import com.pnfsoftware.jeb.util.logging.GlobalLog;
import com.pnfsoftware.jeb.util.logging.ILogger;

/**
 * Diem public plugin #1/3: binary module.
 * 
 * @author Nicolas Falliere
 *
 */
public class DiemIdentifier extends AbstractUnitIdentifier {
    private static final ILogger logger = GlobalLog.getLogger(DiemIdentifier.class);

    public static final String TYPE = "diemvm";
    public static final Version VERSION = Version.create(0, 3);

    public DiemIdentifier() {
        super(TYPE, 0);
    }

    @Override
    public IPluginInformation getPluginInformation() {
        return new PluginInformation("Diem contract parser", "Parser for Diem VM binary modules and scripts (1.0) ",
                "PNF Software", VERSION);
    }

    @Override
    public boolean canIdentify(IInput input, IUnitCreator parent, String name, Map<Object, Object> identmap) {
        if(!checkBytes(input, 0, 'L','I','B','R','A','V','M','\n')) {
            return false;
        }
        if(!checkBytes(input, 0, 'D','I','E','M','V','M','\n')) {
            return false;
        }

        ByteBuffer hdr = input.getHeader();
        if(hdr.limit() < 10) {
            return false;
        }
        hdr.order(ByteOrder.LITTLE_ENDIAN);
        int verMaj = hdr.get(8);
        int verMin = hdr.get(9);
        if(verMaj != 1 || verMin != 0) {
            logger.warn("Unsupported Diem module version: %d.%d", verMaj, verMin);
            return false;
        }

        // successfully identified, proceed with unit preparation and parsing
        return true;
    }

    @Override
    public IUnit prepare(String name, IInput input, IUnitProcessor unitProcessor, IUnitCreator parent, Map<Object, Object> identmap) {
        DiemUnit unit = new DiemUnit(name, input, unitProcessor, parent, pdm);
        return unit;
    }
}
