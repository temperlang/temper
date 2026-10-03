#!/bin/sh
# Builds bank/ with be-cpp, compiles host.cpp against it with ThreadSanitizer,
# and runs each mode. Usage: run.sh [path/to/temper]
#
# Needs clang++ (or $CXX) with -fsanitize=thread; SANITIZE=none builds without
# it. Every mode but crossing must exit 0; crossing must abort with the
# cycle panic. A crash, a hang or a TSan report fails the script.
set -u
here=$(cd "$(dirname "$0")" && pwd)
temper=${1:-$here/../../cli/build/install/temper/bin/temper}
cxx=${CXX:-clang++}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT

cp -R "$here/bank" "$work/bank"
"$temper" build -w "$work/bank" -b cpp >"$work/build.log" 2>&1 || {
    cat "$work/build.log"; exit 1;
}
out=$work/bank/temper.out/cpp
case ${SANITIZE:-thread} in
    none) san= ;;
    *) san=-fsanitize=${SANITIZE:-thread} ;;
esac
"$cxx" -std=c++14 -g -O1 $san -I"$out" \
    "$out/bank/init.cpp" "$here/host.cpp" -o "$work/host" || exit 1

status=0
for mode in threads async bubble reentry crossing; do
    echo "== $mode"
    # crossing takes two locks in opposite orders on purpose; the runtime turns
    # the deadlock into a panic, so TSan's own lock-order report is not wanted.
    extra=
    [ "$mode" = crossing ] && extra=detect_deadlocks=0
    # A deadlock would hang the script; give each mode two minutes (exit 142).
    TSAN_OPTIONS="halt_on_error=1 $extra ${TSAN_OPTIONS:-}" \
        perl -e 'alarm shift; exec @ARGV' 120 "$work/host" "$mode" >"$work/$mode.out" 2>&1
    rc=$?
    # A TSan report is long; its first lines name the race and the frames.
    head -n 12 "$work/$mode.out" | cut -c 1-120
    echo "exit $rc"
    # crossing is expected to panic, which aborts the process (exit 134).
    case $mode in crossing) [ "$rc" = 134 ] || status=1 ;; *) [ "$rc" = 0 ] || status=$rc ;; esac
done
exit $status
