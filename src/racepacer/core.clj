(ns racepacer.core
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.io ByteArrayInputStream]
           [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]
           [javax.sound.sampled AudioFileFormat$Type AudioFormat AudioFormat$Encoding AudioInputStream AudioSystem])
  (:gen-class))

(def ^:private sample-rate 22050.0)
(def ^:private target-format
  (AudioFormat. AudioFormat$Encoding/PCM_SIGNED sample-rate 16 1 2 sample-rate false))

(defn read-race-config
  "Reads race configuration JSON from disk into a Clojure map."
  [path]
  (with-open [reader (io/reader path)]
    (json/read reader :key-fn keyword)))

(defn build-floor-sequence
  "Builds a lazy sequence of spoken floor checkpoints.
   Output entries are maps: {:floor n :arrival-time t}."
  [{:keys [race floorMap]}]
  (let [start-floor (or (:startFloor race) 1)
        ghost-floors (set (:ghostFloors race))
        physical-steps (map-indexed
                        (fn [idx pace]
                          {:floor (+ start-floor idx 1)
                           :pace pace})
                        (mapcat (fn [{:keys [floors pace]}]
                                  (repeat floors pace))
                                floorMap))]
    (->> physical-steps
         (reductions (fn [{:keys [arrival-time]} {:keys [floor pace]}]
                       (if (contains? ghost-floors floor)
                         {:arrival-time arrival-time}
                         (let [next-arrival-time (+ arrival-time pace)]
                           {:arrival-time next-arrival-time
                            :spoken {:floor floor
                                     :arrival-time next-arrival-time}})))
                     {:arrival-time 0.0})
         (map :spoken)
         (remove nil?))))

(defn- arrival-time->parts
  [arrival-time]
  (let [total-seconds (int arrival-time)]
    {:minutes (quot total-seconds 60)
     :seconds (mod total-seconds 60)}))

(defn- arrival-time->mm:ss
  [arrival-time]
  (let [{:keys [minutes seconds]} (arrival-time->parts arrival-time)]
    (format "%d:%02d" minutes seconds)))

(defn- pluralize
  [n word]
  (str n " " word (if (> n 1) "s" "")))

(defn- arrival-time->spoken-text
  [arrival-time]
  (let [{:keys [minutes seconds]} (arrival-time->parts arrival-time)]
    (str (when (pos? minutes)
           (str (pluralize minutes "minute") " "))
         (pluralize seconds "second"))))

(defn floor-callout
  [{:keys [floor arrival-time]}]
  (str "floor " floor " " (arrival-time->spoken-text arrival-time)))

(defn- race-output-stem
  [{:keys [race]}]
  (let [raw-name (or (:name race) "race-pace")
        cleaned (-> raw-name
                    str/lower-case
                    (str/replace #"[^a-z0-9]+" "-")
                    (str/replace #"(^-+|-+$)" ""))]
    (if (str/blank? cleaned) "race-pace" cleaned)))

(defn- run-command!
  [& command]
  (let [proc (.start (ProcessBuilder. ^java.util.List (vec command)))
        exit (.waitFor proc)
        stderr (slurp (.getErrorStream proc))]
    (when-not (zero? exit)
      (throw (ex-info "Command failed"
                      {:command command
                       :exit exit
                       :stderr stderr})))
    true))

(defn- read-clip-samples
  [audio-file]
  (with-open [input (AudioSystem/getAudioInputStream audio-file)
              pcm (AudioSystem/getAudioInputStream target-format input)]
    (let [bytes (.readAllBytes pcm)
          samples (short-array (quot (alength bytes) 2))]
      (loop [sample-idx 0
             byte-idx 0]
        (if (>= byte-idx (alength bytes))
          samples
          (let [low (bit-and 0xFF (aget bytes byte-idx))
                high (aget bytes (inc byte-idx))
                value (short (bit-or low (bit-shift-left high 8)))]
            (aset-short samples sample-idx value)
            (recur (inc sample-idx) (+ byte-idx 2))))))))

(defn- mix-events
  [events]
  (let [total-samples (reduce (fn [max-len {:keys [sample-offset samples]}]
                                (max max-len (+ sample-offset (alength ^shorts samples))))
                              0
                              events)
        mixed (int-array total-samples)]
    (doseq [{:keys [sample-offset samples]} events]
      (loop [i 0]
        (when (< i (alength ^shorts samples))
          (let [target-idx (+ sample-offset i)]
            (aset-int mixed target-idx (+ (aget mixed target-idx)
                                          (aget ^shorts samples i)))
            (recur (inc i))))))
    mixed))

(defn- mixed->wav-bytes
  [mixed-samples]
  (let [out (byte-array (* 2 (alength ^ints mixed-samples)))]
    (loop [i 0
           out-idx 0]
      (if (>= i (alength ^ints mixed-samples))
        out
        (let [sample (aget ^ints mixed-samples i)
              clipped (cond
                        (> sample 32767) 32767
                        (< sample -32768) -32768
                        :else sample)]
          (aset-byte out out-idx (unchecked-byte (bit-and clipped 0xFF)))
          (aset-byte out (inc out-idx) (unchecked-byte (bit-and (bit-shift-right clipped 8) 0xFF)))
          (recur (inc i) (+ out-idx 2)))))))

(defn- delete-tree!
  [f]
  (when (.exists f)
    (when (.isDirectory f)
      (doseq [child (or (.listFiles f) [])]
        (delete-tree! child)))
    (.delete f)))

(defn- build-audio-event
  [tmp-dir idx entry]
  (let [phrase (floor-callout entry)
        clip-file (io/file tmp-dir (format "clip-%03d.wav" idx))
        _ (println (format "[%03d] floor %d at %.3fs -> %s"
                           idx
                           (:floor entry)
                           (double (:arrival-time entry))
                           phrase))
        _ (run-command! "say"
                        "-o" (.getAbsolutePath clip-file)
                        "--file-format=WAVE"
                        "--data-format=LEI16@22050"
                        phrase)
        samples (read-clip-samples clip-file)
        offset (long (Math/round (* sample-rate (:arrival-time entry))))]
    {:sample-offset offset
     :samples samples}))

(defn build-audio-track!
  "Generates a WAV file with each floor callout starting at its arrival-time."
  ([config]
   (let [output-path (str (race-output-stem config) ".wav")]
     (build-audio-track! config output-path)))
  ([config output-path]
   (let [entries (vec (build-floor-sequence config))
         tmp-dir (.toFile (Files/createTempDirectory "racepacer-" (make-array FileAttribute 0)))]
     (println (format "Generating %d callouts into %s" (count entries) output-path))
     (try
       (let [events (map-indexed (partial build-audio-event tmp-dir) entries)
             mixed (mix-events events)
             wav-bytes (mixed->wav-bytes mixed)
             frame-count (quot (alength wav-bytes) (.getFrameSize target-format))]
         (with-open [audio-stream (AudioInputStream. (ByteArrayInputStream. wav-bytes)
                                                     target-format
                                                     frame-count)]
           (AudioSystem/write audio-stream AudioFileFormat$Type/WAVE (io/file output-path))))
         (println (str "Finished writing " output-path))
       (finally
         (delete-tree! tmp-dir)))
     output-path)))

(defn build-floor-sequence-from-file
  [path]
  (-> path
      read-race-config
      build-floor-sequence))

(defn- print-floor-sequence!
  [entries]
  (println "Arrival-time per floor:")
  (doseq [{:keys [floor arrival-time]} entries]
    (println (format "  floor %d -> %s" floor (arrival-time->mm:ss arrival-time))))
  entries)

(defn -main
  [& [path output-path]]
  (let [config (read-race-config (or path "data.json"))
        entries (-> config build-floor-sequence vec)
        _ (print-floor-sequence! entries)
        out (build-audio-track! config (or output-path (str (race-output-stem config) ".wav")))]
    (println (str "Wrote " out))))
