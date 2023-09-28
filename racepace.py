from pydub import AudioSegment
import json
import os


def add_silence(audio, target_duration_ms):
    current_duration_ms = len(audio)
    silence_duration_ms = target_duration_ms - current_duration_ms

    if silence_duration_ms > 0:
        silence = AudioSegment.silent(duration=silence_duration_ms)
        extended_audio = audio + silence
        return extended_audio


def say_to_file(words, fn):
    _ = os.popen('espeak "%s" -w %s' % (words, fn)).readlines()
    return AudioSegment.from_wav(fn)


def to_timestr(time_in_seconds):
    time_in_seconds = int(time_in_seconds)
    minutes = time_in_seconds / 60
    seconds = time_in_seconds % 60
    out = ""
    if minutes > 0:
        out = "%d minutes, " % (minutes)
    out = out + " %d seconds" % (seconds)
    return out


def expand(floormap):
    audio = AudioSegment.from_wav("/tmp/go.wav")
    floor = 0
    time = 0.0
    for (floors, pace) in floormap:
        for _ in range(floors):
            floor = floor + 1
            time = time + pace
            audio = add_silence(audio, time * 1000)
            text = "floor %d %s" % (floor, to_timestr(time))
            fn = "/tmp/%d.wav" % (floor)
            clip = say_to_file(text, fn)
            audio = audio + clip
    audio.export("pace.mp3", format="mp3")


if __name__ == "__main__":
    with open('data.json', 'r') as file:
        data = json.load(file)
        expand(data)
