ZDOTDIR="$MEND_ORIGINAL_ZDOTDIR"
[[ -f "$ZDOTDIR/.zshrc" ]] && source "$ZDOTDIR/.zshrc"
source "$MEND_STARTUP_DIR/session.sh"
PROMPT="[mend unlocked] $PROMPT"
unset MEND_ORIGINAL_ZDOTDIR
