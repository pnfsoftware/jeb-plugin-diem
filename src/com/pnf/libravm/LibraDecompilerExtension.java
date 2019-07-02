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

import com.pnf.libravm.Libra.OpcodeDef;
import com.pnfsoftware.jeb.core.units.code.asm.ChainedOperationResult;
import com.pnfsoftware.jeb.core.units.code.asm.cfg.CFG;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.AbstractNativeDecompilerExtension;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.INativeDecompilationTarget;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.INativeDecompilerUnit;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ir.IEStatement;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ir.IEUntranslatedInstruction;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.ir.opt.EMasterOptimizer;
import com.pnfsoftware.jeb.core.units.code.asm.decompiler.opt.IOptFilterCanDiscard;
import com.pnfsoftware.jeb.core.units.code.asm.type.IWildcardType;
import com.pnfsoftware.jeb.core.units.code.asm.type.IWildcardTypeManager;

/**
 * Primary decompiler extension, methods are guaranteed to be executed in a Libra context.
 * 
 * @author Nicolas Falliere
 *
 */
public class LibraDecompilerExtension extends AbstractNativeDecompilerExtension {

    @Override
    public ChainedOperationResult<Boolean> customizeIntermediateOptimizer(INativeDecompilerUnit<?> decompiler,
            EMasterOptimizer optimizer) {

        optimizer.addDisregardedOutputFilter(new IOptFilterCanDiscard() {
            @Override
            public boolean check(CFG<IEStatement> cfg, long insnAddress, int regDef) {
                // routine-context variables can always be discarded if they reach a RET
                return regDef < 0;
            }
        });

        return ChainedOperationResult.TRUE_CONTINUE;
    }

    @Override
    public ChainedOperationResult<Boolean> applyAdditionalTypes(INativeDecompilationTarget target,
            CFG<IEStatement> cfg) {

        // create/retrieve wildcard types equivalent of the custom pseudo-native libra types (that were created by the analyzer extension)
        IWildcardTypeManager etypeman = target.getDecompiler().getWildcardTypeManager();
        IWildcardType tBool = etypeman.create("bool");
        IWildcardType tAddress = etypeman.create("address");

        for(IEStatement stm: cfg.instructions()) {
            if(stm instanceof IEUntranslatedInstruction) {
                IEUntranslatedInstruction u = (IEUntranslatedInstruction)stm;
                if(u.getTag() instanceof Integer) {
                    OpcodeDef opcode = OpcodeDef.fromValue((int)u.getTag());
                    switch(opcode) {
                    case ASSERT:
                        u.getParameterExpression(1).setType(tBool);
                        break;
                    case GET_TXN_SENDER:
                        u.getResultExpression().setType(tAddress);
                        break;
                    default:
                        ;
                    }
                }
            }
        }

        return ChainedOperationResult.TRUE_CONTINUE;
    }
}
