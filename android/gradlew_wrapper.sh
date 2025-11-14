#!/bin/sh
# Wrapper script to ensure PATH includes standard directories for gradlew
export PATH="/usr/bin:/bin:/usr/sbin:/sbin:${PATH}"
exec "$(dirname "$0")/gradlew" "$@"
