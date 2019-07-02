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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

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
 * Libra public plugin #1/3: binary module.
 * 
 * @author Nicolas Falliere
 *
 */
public class LibraIdentifier extends AbstractUnitIdentifier {
    private static final ILogger logger = GlobalLog.getLogger(LibraIdentifier.class);

    public static final String TYPE = "libravm";
    public static final Version VERSION = Version.create(0, 1);

    public LibraIdentifier() {
        super(TYPE, 0);
    }

    @Override
    public IPluginInformation getPluginInformation() {
        return new PluginInformation("Libra contract parser", "Parser for Libra VM binary modules and scripts (1.0) ",
                "PNF Software", VERSION);
    }

    @Override
    public boolean canIdentify(IInput input, IUnitCreator parent) {
        if(!checkBytes(input, 0, 'L','I','B','R','A','V','M','\n')) {
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
            logger.warn("Unsupported Libra module version: %d.%d", verMaj, verMin);
            return false;
        }

        // successfully identified, proceed with unit preparation and parsing
        return true;
    }

    @Override
    public IUnit prepare(String name, IInput input, IUnitProcessor unitProcessor, IUnitCreator parent) {
        LibraUnit unit = new LibraUnit(name, input, unitProcessor, parent, pdm);
        return unit;
    }
}
