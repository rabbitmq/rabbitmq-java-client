@echo off

setlocal EnableDelayedExpansion

set SCRIPT_DIR="%~dp0%"
set CP=
for %%F in (*.jar !SCRIPT_DIR!/../build/lib/*.jar !SCRIPT_DIR!/../lib/*.jar) ^
DO set CP=!CP!;%%F

java -cp %CP% %1 %2 %3 %4 %5 %6 %7 %8 %9

endlocal
