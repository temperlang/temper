# IO

Basic input/output operations for interactive programs.

## Sleep

Pause execution for the given number of milliseconds.

    @connected("stdSleep")
    export let sleep(ms: Int): Promise<Empty> {
      panic()
    }

## Read Line

Read one line from standard input. Returns null on EOF.

    @connected("stdReadLine")
    export let readLine(): Promise<String?> {
      panic()
    }

## Terminal Size

Get the current terminal dimensions in characters.
Returns a default of 80x24 if the terminal size cannot be determined.

    @connected("stdTermCols")
    export let terminalColumns(): Int {
      panic()
    }

    @connected("stdTermRows")
    export let terminalRows(): Int {
      panic()
    }

