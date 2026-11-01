# Loaded by our Bash/Zsh startup file after the user's normal shell configuration.
# This is static code: the supervisor sends the values below through an anonymous stdin pipe.
# The credential authorizes broker requests; the private key stays in the separate key JVM.

# Disable command/input tracing before reading secrets, and stop automatic variable exporting.
set +x
set +v
set +a
# Read exactly the four lines written by SessionShell.launch(), in the same order.
# IFS= and read -r preserve the values without trimming whitespace or interpreting backslashes.
IFS= read -r _mend_credential || exit 1
IFS= read -r _mend_socket || exit 1
IFS= read -r _mend_java || exit 1
IFS= read -r _mend_classpath || exit 1
readonly _mend_credential _mend_socket _mend_java _mend_classpath
# Bootstrap input is finished. Read subsequent interactive commands from the actual terminal.
exec < /dev/tty || exit 1
# Enable job control so foreground pipelines, Ctrl+Z and fg behave like a normal shell.
set -m
unset MEND_STARTUP_DIR
# Ensure a user alias cannot take precedence over the session functions defined below.
unalias dec merge lock help mend 2>/dev/null || :

_mend_request() {
    # Disable tracing before touching the credential, even if enabled interactively.
    set +x
    set +v
    # The shell builtin sends the credential to the client's stdin, without putting it in argv.
    # The client sends an authenticated Unix-socket request and forwards the broker's response
    # to its own stdout/stderr, so normal shell pipes and redirections work.
    builtin printf '%s\n' "$_mend_credential" |
        "$_mend_java" -cp "$_mend_classpath" co.samco.mend4.desktop.MainKt \
        --session-client "$_mend_socket" "$@"
}
dec() { _mend_request dec "$@"; }
merge() { _mend_request merge "$@"; }
help() { _mend_request help "$@"; }
# Ask the key process to stop, then exit this shell so the supervisor can finish cleanup.
# Arguments may request help or report an error; only a plain lock should exit the shell.
lock() {
    _mend_request lock "$@"
    local request_status=$?
    if [[ $# -eq 0 ]]; then exit "$request_status"; fi
    return "$request_status"
}
# Keep global commands (including nested unlock) outside this session.
mend() {
    builtin printf '%s\n' 'mend is unavailable in this session; use help to see session commands.' >&2
    return 2
}

_mend_prompt() {
    # A fresh prompt counts as activity. Enter at an empty prompt invokes this hook too.
    # If the broker has expired or stopped, leave the shell; preserve the previous command's
    # exit status when the touch succeeds.
    local previous_status=$?
    _mend_request touch || exit
    return "$previous_status"
}
# Register the hook using each shell's mechanism, retaining any existing prompt hooks.
if [[ -n ${BASH_VERSION-} ]]; then
    PROMPT_COMMAND=(_mend_prompt "${PROMPT_COMMAND[@]}")
else
    precmd_functions=(_mend_prompt "${precmd_functions[@]}")
fi
