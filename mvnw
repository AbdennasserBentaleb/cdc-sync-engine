#!/bin/sh
# ----------------------------------------------------------------------------
# Licensed to the Apache Software Foundation (ASF) (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
# ----------------------------------------------------------------------------

# ----------------------------------------------------------------------------
# Apache Maven Wrapper startup script for Unix-like systems, version 3.3.0
# ----------------------------------------------------------------------------

set -e

DIRNAME=$(dirname "$0")
WRAPPER_PROPERTIES="$DIRNAME/.mvn/wrapper/maven-wrapper.properties"
WRAPPER_JAR="$DIRNAME/.mvn/wrapper/maven-wrapper.jar"

if [ ! -f "$WRAPPER_JAR" ]; then
    WRAPPER_URL=$(grep "wrapperUrl" "$WRAPPER_PROPERTIES" | cut -d'=' -f2)
    echo "Downloading Maven Wrapper JAR: $WRAPPER_URL"
    curl -sLo "$WRAPPER_JAR" "$WRAPPER_URL"
fi

if [ -z "$JAVA_HOME" ]; then
    JAVA_EXE="java"
else
    JAVA_EXE="$JAVA_HOME/bin/java"
fi

exec "$JAVA_EXE" -classpath "$WRAPPER_JAR" \
  "-Dmaven.home=$DIRNAME" \
  "-Dmaven.multiModuleProjectDirectory=$DIRNAME" \
  org.apache.maven.wrapper.MavenWrapperMain "$@"
