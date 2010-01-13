@echo off

setlocal EnableDelayedExpansion

set CP=
for %%F in ("%~dp0"/*.jar) do set CP=!CP!;"%%F"

java -cp %CP% %1 %2 %3 %4 %5 %6 %7 %8 %9

endlocal
