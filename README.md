# racepacer

Generate pacing audio for stair races from a JSON race map.

## Requirements

- Clojure/Leiningen
- macOS `say` command (used to synthesize spoken floor callouts)

## Usage

Generate a WAV file from `data.json`:

```zsh
lein run
```

Generate from a specific input file and output path:

```zsh
lein run data.json my-race.wav
```

The output file speaks each floor at its exact `arrival-time` from the computed sequence.

## Test

```zsh
lein test
```

## License

Copyright © 2026 FIXME

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at https://www.gnu.org/software/classpath/license.html.
