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

import com.pnfsoftware.jeb.core.units.PoolEntry;
import com.pnfsoftware.jeb.util.format.TextBuilder;
import com.pnfsoftware.jeb.util.serialization.annotations.Ser;
import com.pnfsoftware.jeb.util.serialization.annotations.SerId;

/**
 * Super class for all Diem objects stored in indexed pools.
 *
 * @author Nicolas Falliere
 *
 */
@Ser
public abstract class DiemPoolEntry extends PoolEntry implements DiemObject {
    @SerId(1)
    int fileOffset;
    @SerId(2)
    int fileSize;
    @SerId(3)
    long mappedAddress;
    @SerId(4)
    int mappedSize;

    public DiemPoolEntry() {
    }

    public void setFilePosition(int offset, int size) {
        this.fileOffset = offset;
        this.fileSize = size;
    }

    public void setMemoryMappingInformation(long address, int size) {
        this.mappedAddress = address;
        this.mappedSize = size;
    }
    
    @Override
    public TextBuilder format(DiemUnit l, TextBuilder t) {
        return t.append(toString());
    }
}
