#!/bin/bash
# Build classpath from libraries and existing bin
CLASSPATH="bin"
for jar in libraries/*.jar; do
    CLASSPATH="$CLASSPATH:$jar"
done

echo "Compiling with classpath: $CLASSPATH"
javac -d bin \
    -cp "$CLASSPATH" \
    src/pomdp/*.java \
    src/solver/*.java \
    src/main/*.java \
    src/iot/*.java \
    src/mapek/*.java \
    src/charts/*.java 2>&1

if [ $? -eq 0 ]; then
    echo "Compilation successful!"
else
    echo "Compilation failed"
    exit 1
fi
