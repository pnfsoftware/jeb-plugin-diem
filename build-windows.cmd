@echo off
ant -f scripts\build.xml -DpluginClassname="com.pnf.diemvm.DiemIdentifier com.pnf.diemvm.DiemDisassemblerPlugin com.pnf.diemvm.DiemDecompilerPlugin" -DpluginFilename=JebDiemPlugin -DpluginVersion=0.4.2