# T3TR0S-bare

This is a bare version of single player
[T3TR0S](http://github.com/imalooney/t3tr0s), because we miss the accessibility
and simplicity of its code before features become a priority.

## Setup

1. Install [Leiningen](http://leiningen.org)
1. Run `lein cljsbuild auto` to run the auto-compiler.
1. Play the game by opening `public/index.html`

## REPL

You can start a REPL for interacting with the running game with:

```
lein repl
> (brepl)
```
