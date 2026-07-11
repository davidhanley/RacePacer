(ns racepacer.core-test
  (:require [clojure.test :refer :all]
            [racepacer.core :as core]))

(deftest single-pace-10-floor-shape
  (let [config {:race {}
                :floorMap [{:floors 10 :pace 12.0}]}
        result (core/build-floor-sequence config)]
    (is (instance? clojure.lang.LazySeq result))
    (is (= [{:floor 2 :arrival-time 12.0}
            {:floor 3 :arrival-time 24.0}
            {:floor 4 :arrival-time 36.0}
            {:floor 5 :arrival-time 48.0}
            {:floor 6 :arrival-time 60.0}
            {:floor 7 :arrival-time 72.0}
            {:floor 8 :arrival-time 84.0}
            {:floor 9 :arrival-time 96.0}
            {:floor 10 :arrival-time 108.0}
            {:floor 11 :arrival-time 120.0}]
           (vec result)))))

(deftest three-pace-10-floor-shape
  (let [config {:race {}
                :floorMap [{:floors 3 :pace 12.0}
                           {:floors 4 :pace 10.5}
                           {:floors 3 :pace 9.0}]}
        result (vec (core/build-floor-sequence config))]
    (is (= [{:floor 2 :arrival-time 12.0}
            {:floor 3 :arrival-time 24.0}
            {:floor 4 :arrival-time 36.0}
            {:floor 5 :arrival-time 46.5}
            {:floor 6 :arrival-time 57.0}
            {:floor 7 :arrival-time 67.5}
            {:floor 8 :arrival-time 78.0}
            {:floor 9 :arrival-time 87.0}
            {:floor 10 :arrival-time 96.0}
            {:floor 11 :arrival-time 105.0}]
           result))))

(deftest ghost-floor-10-floor-shape
  (let [config {:race {:startFloor 10
                       :ghostFloors [13 17]}
                :floorMap [{:floors 10 :pace 10.5}]}
        result (vec (core/build-floor-sequence config))]
    (is (= [{:floor 11 :arrival-time 10.5}
            {:floor 12 :arrival-time 21.0}
            {:floor 14 :arrival-time 31.5}
            {:floor 15 :arrival-time 42.0}
            {:floor 16 :arrival-time 52.5}
            {:floor 18 :arrival-time 63.0}
            {:floor 19 :arrival-time 73.5}
            {:floor 20 :arrival-time 84.0}]
           result))))

(deftest floor-callout-formatting
  (is (= "floor 2 12 seconds"
         (core/floor-callout {:floor 2 :arrival-time 12.0})))
  (is (= "floor 14 31 seconds"
         (core/floor-callout {:floor 14 :arrival-time 31.5})))
  (is (= "floor 15 1 minute 1 second"
         (core/floor-callout {:floor 15 :arrival-time 61.0}))))

