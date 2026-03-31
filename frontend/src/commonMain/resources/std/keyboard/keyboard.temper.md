# Keyboard

Keyboard input for interactive programs.

## Next Keypress

Wait for and return the next keypress. Returns the key name as a string
(e.g. "a", "ArrowUp", "Enter", "Escape"). Returns null on EOF.

    @connected("stdNextKeypress")
    export let nextKeypress(): Promise<String?> {
      panic()
    }
