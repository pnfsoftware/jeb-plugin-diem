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

import com.pnfsoftware.jeb.core.units.Pool;
import com.pnfsoftware.jeb.util.format.TextBuilder;
import com.pnfsoftware.jeb.util.serialization.annotations.Ser;

/**
 * Super class for pools containing indexed Diem objects.
 *
 * @author Nicolas Falliere
 *
 * @param <T>
 */
@Ser
public class DiemPool<T extends DiemPoolEntry> extends Pool<T> implements DiemObject {

    public DiemPool(String poolname) {
        super(poolname);
    }
    
    @Override
    public TextBuilder format(DiemUnit l, TextBuilder t) {
        t.append("%s: [", getName()).indent(true);
        for(T e: this) {
            e.format(l, t);
            t.append(',').appendLine();
        }
        return t.unindent().append("]");
    }
}
