@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion
title Сборка мода Martial Arts

echo ============================================
echo   Сборка мода "Боевые искусства" (1.20.1)
echo ============================================
echo.

rem ---------- 1. Проверка Java (нужна 17..22) ----------
set "JAVA_EXE=java"
if defined JAVA_HOME set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
"%JAVA_EXE%" -version 2> "%TEMP%\ma_java.txt"
if errorlevel 1 goto nojava
set "JV="
for /f "tokens=3" %%v in ('findstr /i "version" "%TEMP%\ma_java.txt"') do if not defined JV set "JV=%%~v"
if not defined JV goto nojava
for /f "delims=." %%m in ("!JV!") do set "JMAJ=%%m"
if "!JMAJ!"=="1" set "JMAJ=8"
if !JMAJ! LSS 17 goto badjava
if !JMAJ! GTR 22 goto badjava
echo [OK] Java !JV!
echo.

rem ---------- 2. Копия проекта в путь без русских букв и пробелов ----------
set "SRC=%~dp0"
set "WORK=C:\mods-build\martialarts"
set "GRADLE_USER_HOME=C:\mods-build\gradle-home"
echo Копирую проект в %WORK% ...
robocopy "%SRC%." "%WORK%" /E /NFL /NDL /NJH /NJS /NP /XD .gradle build .idea run run-data /XF *.jar.tmp >nul
if errorlevel 8 (
  echo [ОШИБКА] Не удалось скопировать проект в %WORK%
  goto fail
)
copy /y "%SRC%gradle\wrapper\gradle-wrapper.jar" "%WORK%\gradle\wrapper\gradle-wrapper.jar" >nul

rem ---------- 3. Сборка ----------
cd /d "%WORK%"
echo Собираю мод. Первый раз скачивается Minecraft и Forge — это 5-15 минут, не закрывайте окно...
echo.
call gradlew.bat build --no-daemon --stacktrace
if errorlevel 1 (
  echo.
  echo [ОШИБКА] Сборка не удалась. Скопируйте текст выше и отправьте его.
  goto fail
)

copy /y "%WORK%\build\libs\martialarts-*.jar" "%SRC%" >nul
echo.
echo ============================================
echo   ГОТОВО! Файл мода:
echo   %SRC% (martialarts-*.jar)
echo   Положите его в .minecraft\mods вместе с PlayerAnimator.
echo ============================================
pause
exit /b 0

:nojava
echo [ОШИБКА] Java не найдена.
echo Установите Java 17: https://adoptium.net/temurin/releases/?version=17
echo При установке включите пункт "Set JAVA_HOME variable", затем запустите этот файл снова.
goto fail

:badjava
echo [ОШИБКА] Найдена Java !JV!, а для сборки нужна Java 17 (подходят 17-22).
echo Установите Java 17: https://adoptium.net/temurin/releases/?version=17
echo При установке включите пункт "Set JAVA_HOME variable", затем запустите этот файл снова.
goto fail

:fail
echo.
pause
exit /b 1
