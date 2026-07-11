# racepacer

Generate pacing audio for stair races from a JSON race map.

This project reads a race configuration, expands it into floor-by-floor pacing checkpoints, and then generates a spoken audio track where each checkpoint is announced at the correct time.

The current implementation writes a `.wav` file and uses the macOS `say` command to synthesize speech.

## Requirements

- Clojure/Leiningen
- macOS `say` command (used to synthesize spoken floor callouts)

## What it does

Given a race config like this:

```json
{
  "race": {
	"name": "US bank LA",
	"startFloor": 1,
	"ghostFloors": [13]
  },
  "floorMap": [
	{ "floors": 5, "pace": 12.0 },
	{ "floors": 71, "pace": 10.5 }
  ]
}
```

the app:

- starts counting from `startFloor + 1` for the first spoken floor
- skips any floors listed in `ghostFloors`
- keeps the original pace timing without rounding
- says each checkpoint as text like `floor 2 12 seconds`
- writes the output audio file using the race name when available

## Input format

The JSON file has two top-level keys:

- `race`
  - `name` — used to name the output file
  - `startFloor` — optional, defaults to `1`
  - `ghostFloors` — optional list of floors to omit entirely
- `floorMap`
  - list of pace segments
  - each segment has:
	- `floors` — number of floors in that pace segment
	- `pace` — seconds per floor for that segment

## Usage

Generate a WAV file from `data.json`:

```zsh
lein run
```

Generate from a specific input file and output path:

```zsh
lein run data.json my-race.wav
```

If you only pass an input file, the output name is based on the race name.

## Output

The generated audio is a single spoken pacing track. Each spoken checkpoint is aligned to its `arrival-time`, so if the sequence includes:

```clojure
{:floor 2 :arrival-time 12.0}
```

the resulting audio will say:

```text
floor 2 12 seconds
```

at the 12-second mark in the file.

## Test

```zsh
lein test
```

## Notes

- Ghost floors are omitted entirely from the spoken output.
- The project currently writes `.wav` output; you can convert it to other formats afterward if needed.
- The generated file can be long for large stair races, so expect a little processing time.

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
