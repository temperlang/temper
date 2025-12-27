# CLI

The CLI for interacting with temper. Contains subcommands for different aspects.

## Install
`gradle cli:deploy` to install this. Will print the whole path to where it is available

## Run via gradle
`gradle cli:temper -PcliArgs='<your args here>'` runs the cli through gradle

## Subcommands

### REPL

A REPL for working with temper

#### Extra Commands
The REPL provides a few extra commands that aren't part of the language to help debug.
##### Describe
```
$ 1+1
interactive#1: 2
$ describe(1, "")
interactive#2: void
$ describe(1, "frontend.defineStage.after")
Describe interactive#1 @ frontend.defineStage.after
  2;

 ```
Shows an example using the describe function to find out more about a previously executed statement. The two arguments are the number that references which statement to consider and which stage to show information from. Autocompletion can enumerate stages or see Debug.kt for a listing


### Run

Runs a piece of temper code displaying the output.
Defaults to using the interpreter, using the interpreter caps the total complexity of the program run.

### Test

Runs the unit tests defined in a specific file.
Defaults to using the interpreter, using the interpreter caps the total complexity of the tests run.
