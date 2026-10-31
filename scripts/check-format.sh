#!/usr/bin/env bash
# The same formatting check the CI `format` job runs, so it can be run before a push instead of
# after a red build. It exists because "it looked aligned" is not the check: clang-format's own
# line-breaking (a call that fits in 120 columns goes on one line, an argument list that does not
# gets one name per line) is not something a person reproduces by eye, and a hand-written call
# that reads fine will still fail.
#
# The version matters as much as the invocation: CI installs clang-format-18 rather than whatever
# the runner ships, because an image bump would otherwise fail every PR against a version nobody
# was told about. This script uses the same binary name and says how to get it.
#
# Usage:
#   scripts/check-format.sh          # report what is wrong, exit non-zero if anything is
#   scripts/check-format.sh --fix    # rewrite the files in place
set -u

cd "$(dirname "$0")/.." || exit 1

BIN="clang-format-18"
if ! command -v "$BIN" > /dev/null 2>&1; then
    # A plain `clang-format` is whatever the machine has; it is not a substitute, so say so rather
    # than run it and report a result the CI will contradict.
    echo "$BIN not found. CI pins version 18; install it to match:" >&2
    echo "  apt-get install -y clang-format-18        # Debian/Ubuntu" >&2
    echo "  brew install clang-format                 # macOS (check --version is 18.x)" >&2
    echo "  pip install clang-format==18.*            # anywhere, brings the binary" >&2
    exit 2
fi

# The same roots and extensions CI checks. examples/ is deliberately out of scope there, so it is
# out of scope here: a check that is stricter than CI fails work CI would have accepted.
FILES=()
while IFS= read -r -d '' f; do
    FILES+=("$f")
done < <(find core cli server desktop tests -type f \
    \( -name '*.cpp' -o -name '*.h' -o -name '*.mm' \) -print0)

if [ "${1:-}" = "--fix" ]; then
    printf '%s\0' "${FILES[@]}" | xargs -0 "$BIN" -i
    echo "formatted ${#FILES[@]} files with $("$BIN" --version)"
    exit 0
fi

printf '%s\0' "${FILES[@]}" | xargs -0 "$BIN" --dry-run --Werror
status=$?
if [ $status -eq 0 ]; then
    echo "format: ${#FILES[@]} files clean ($("$BIN" --version))"
fi
exit $status