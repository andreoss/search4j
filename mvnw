#!/bin/sh
# Maven Wrapper script for Apache Maven 3.9.9

# Determine the project base directory
PRG="$0"
while [ -h "$PRG" ]; do
    ls=$(ls -ld "$PRG")
    link=$(expr "$ls" : '.*-> \(.*\)$')
    if expr "$link" : '/.*' > /dev/null; then
        PRG="$link"
    else
        PRG=$(dirname "$PRG")/"$link"
    fi
done
SAVED="$(pwd)"
cd "$(dirname "$PRG")/" >/dev/null
APP_HOME="$(pwd -P)"
cd "$SAVED" >/dev/null

MAVEN_OPTS="${MAVEN_OPTS} -Xmx2g"
export MAVEN_OPTS

WRAPPER_PROPERTIES="$APP_HOME/.mvn/wrapper/maven-wrapper.properties"
WRAPPER_JAR="$APP_HOME/.mvn/wrapper/maven-wrapper.jar"

if [ -f "$APP_HOME/mvnw.cmd" ]; then
    # On cygwin/mingw, ensure paths are in Unix format before calling mvnw.cmd
    "$APP_HOME/mvnw.cmd" "$@"
else
    # Locate Java
    if [ -n "$JAVA_HOME" ]; then
        JAVA="$JAVA_HOME/bin/java"
    else
        JAVA="$(command -v java 2>/dev/null)"
    fi
    if [ -z "$JAVA" ]; then
        echo "ERROR: JAVA_HOME is not set and no 'java' command could be found in PATH." >&2
        exit 1
    fi

    # Run Maven
    exec "$JAVA" \
        $MAVEN_OPTS \
        -classpath "$WRAPPER_JAR" \
        "-Dmaven.wrapper.properties=$WRAPPER_PROPERTIES" \
        org.apache.maven.wrapper.MavenWrapperMain \
        "$@"
fi
