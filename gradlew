#!/bin/sh
#
# Gradle wrapper startup script (simplified POSIX sh).
# Downloads and runs the Gradle distribution declared in
# gradle/wrapper/gradle-wrapper.properties on first use.

set -e

APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

exec "$JAVACMD" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
