@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM    http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@REM ----------------------------------------------------------------------------
@REM Apache Maven Wrapper startup script for Windows, version 3.3.0
@REM
@REM Optional ENV vars
@REM   MVNW_REPOURL - repo url base for downloading maven distribution
@REM   MVNW_USERNAME/MVNW_PASSWORD - user and password for authentication
@REM   MVNW_VERBOSE - verbose logging for wrapper
@REM ----------------------------------------------------------------------------

@IF "%DEBUG%" == "true" @ECHO ON
@setlocal

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.\

@REM Enable HTTP/2 for downloads if supported
set "MAVEN_OPTS=-Djdk.http.authproxy.allow.ntlm=true %MAVEN_OPTS%"

@REM Find the project root directory
set WRAPPER_JAR="%DIRNAME%\.mvn\wrapper\maven-wrapper.jar"
set WRAPPER_PROPERTIES="%DIRNAME%\.mvn\wrapper\maven-wrapper.properties"
set MAVEN_PROJECTBASEDIR=%MAVEN_BASEDIR%
if not "%MAVEN_PROJECTBASEDIR%" == "" goto endReadBaseDir

set MAVEN_PROJECTBASEDIR=%DIRNAME%
:findBaseDir
if exist "%MAVEN_PROJECTBASEDIR%\.mvn" goto endReadBaseDir
set "MAVEN_PROJECTBASEDIR=%MAVEN_PROJECTBASEDIR%.."
if exist "%MAVEN_PROJECTBASEDIR%" goto findBaseDir

:endReadBaseDir

@REM Find JAVA_HOME
if not "%JAVA_HOME%" == "" goto checkJava

set "JAVA_EXE=java.exe"
%JAVA_EXE% -version >NUL 2>&1
if "%ERRORLEVEL%" == "0" goto runBatch

echo.
echo Error: JAVA_HOME is not defined correctly.
echo   We cannot execute %JAVA_EXE%
echo.
exit /b 1

:checkJava
set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
if exist "%JAVA_EXE%" goto runBatch

echo.
echo Error: JAVA_HOME is set to an invalid directory.
echo   JAVA_HOME = "%JAVA_HOME%"
echo   Please set the JAVA_HOME variable in your environment to match the
echo   location of your Java installation.
echo.
exit /b 1

:runBatch
@REM Download wrapper jar if not present
if exist %WRAPPER_JAR% goto runMaven

@REM Check if curl is available
set "CURL_EXE=curl"
%CURL_EXE% --version >NUL 2>&1
if not "%ERRORLEVEL%" == "0" goto noCurl

@REM Get wrapper version from properties
for /f "tokens=2 delims==" %%i in ('findstr /i "wrapperUrl" %WRAPPER_PROPERTIES%') do set WRAPPER_URL=%%i

echo Downloading: %WRAPPER_URL%
%CURL_EXE% -sLo %WRAPPER_JAR% %WRAPPER_URL%
if not "%ERRORLEVEL%" == "0" (
  echo.
  echo Error: Failed to download %WRAPPER_URL%
  echo.
  exit /b 1
)

:runMaven
set "MAVEN_CMD_LINE_ARGS=%*"
@REM Remove trailing backslash if present to avoid escaping the closing quote
set "MAVEN_PROJECT_SAFE=%MAVEN_PROJECTBASEDIR%"
if "%MAVEN_PROJECT_SAFE:~-1%"=="\" set "MAVEN_PROJECT_SAFE=%MAVEN_PROJECT_SAFE:~0,-1%"

"%JAVA_EXE%" %MAVEN_OPTS% -classpath %WRAPPER_JAR% "-Dmaven.home=%MAVEN_PROJECT_SAFE%" "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECT_SAFE%" org.apache.maven.wrapper.MavenWrapperMain %MAVEN_CMD_LINE_ARGS%

if ERRORLEVEL 1 exit /b 1
goto :EOF

:noCurl
echo.
echo Error: curl is not available. Please install curl or manually download the maven-wrapper.jar to %WRAPPER_JAR%
echo.
exit /b 1
