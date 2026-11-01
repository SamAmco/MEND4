# No secrets in this file. The parent writes four lines through an anonymous stdin pipe.
set +x
set +v
set +a
IFS= read -r _mend_credential || exit 1
IFS= read -r _mend_socket || exit 1
IFS= read -r _mend_java || exit 1
IFS= read -r _mend_classpath || exit 1
readonly _mend_credential _mend_socket _mend_java _mend_classpath
exec < /dev/tty || exit 1
set -m
unset MEND_STARTUP_DIR
unalias dec merge lock mend 2>/dev/null || :

_mend_request() {
    # Disable tracing before touching the credential, even if enabled interactively.
    set +x
    set +v
    builtin printf '%s\n' "$_mend_credential" |
        "$_mend_java" -cp "$_mend_classpath" co.samco.mend4.desktop.MainKt \
        --session-client "$_mend_socket" "$@"
}
dec() { _mend_request dec "$@"; }
merge() { _mend_request merge "$@"; }
lock() { _mend_request lock; exit; }
mend() {
    builtin printf '%s\n' 'mend is unavailable in this session; use dec, merge or lock.' >&2
    return 2
}

_mend_prompt() {
    local previous_status=$?
    _mend_request touch || exit
    return "$previous_status"
}
if [[ -n ${BASH_VERSION-} ]]; then
    PROMPT_COMMAND=(_mend_prompt "${PROMPT_COMMAND[@]}")
else
    precmd_functions=(_mend_prompt "${precmd_functions[@]}")
fi
