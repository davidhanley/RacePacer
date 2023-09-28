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

def say_to_file( words, fn ):
    v = os.popen( 'espeak "%s" -w %s' % (words,fn)).readlines()
    return AudioSegment.from_wav(fn)

def to_timestr(tis):
    tis = int(tis)
    m = tis / 60
    s = tis % 60
    out = ""
    if m>0:
        out = "%d minutes, " %( m)
    out = out + " %d seconds" % (s)
    return out

def expand(floormap):
    audio =  AudioSegment.from_wav( "/tmp/go.wav" )
    floor = 0
    time = 0.0
    for (f,p) in floormap:
        for _ in range(f):
            floor = floor + 1
            time = time + p
            audio = add_silence(audio , time * 1000)
            text = "floor %d %s" % (floor ,to_timestr(time))
            fn = "/tmp/%d.wav" % (floor)
            clip = say_to_file(text, fn)
            print(clip)
            audio = audio + clip
    audio.export("pace.mp3", format="mp3")

if __name__ == "__main__":
    with open('data.json', 'r') as file:
        data = json.load(file)
        expand(data)



