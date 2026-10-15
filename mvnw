#!/bin/sh
# Lightweight Maven launcher: use a system Maven when available, otherwise bootstrap Maven 3.9.11.
set -eu
MAVEN_VERSION="3.9.11"
if command -v mvn >/dev/null 2>&1; then
  exec mvn "$@"
fi
MAVEN_HOME="$HOME/.m2/wrapper/dists/apache-maven-${MAVEN_VERSION}"
if [ ! -x "$MAVEN_HOME/bin/mvn" ]; then
  command -v curl >/dev/null 2>&1 || { echo "Maven is not installed and curl was not found. Install Maven 3.6.3+ or curl." >&2; exit 1; }
  command -v tar >/dev/null 2>&1 || { echo "tar is required to bootstrap Maven." >&2; exit 1; }
  mkdir -p "$MAVEN_HOME"
  TMP_ARCHIVE="$MAVEN_HOME/apache-maven-${MAVEN_VERSION}-bin.tar.gz"
  echo "Downloading Apache Maven ${MAVEN_VERSION}..."
  curl -fL "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/${MAVEN_VERSION}/apache-maven-${MAVEN_VERSION}-bin.tar.gz" -o "$TMP_ARCHIVE"
  tar -xzf "$TMP_ARCHIVE" -C "$MAVEN_HOME" --strip-components=1
  rm -f "$TMP_ARCHIVE"
fi
exec "$MAVEN_HOME/bin/mvn" "$@"
