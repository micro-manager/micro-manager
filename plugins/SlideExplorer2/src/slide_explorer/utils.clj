(ns slide-explorer.utils)

(def testing
  "An atom that indicates if we are currently
   in testing mode."
  (atom false))

;; identify OS

(defn get-os
  "Returns the operating system name as provided
   by the JVM."
  []
  (.. System (getProperty "os.name") toLowerCase))

(def is-win
  "Returns true if we are running on Windows."
  (memoize #(not (neg? (.indexOf (get-os) "win")))))

(def is-mac
  "Returns true if we are running on Mac"
  (memoize #(not (neg? (.indexOf (get-os) "mac")))))

(def is-unix
  "Returns trun if we are running on Linux or Unix."
  (memoize #(not (and (neg? (.indexOf (get-os) "nix"))
                      (neg? (.indexOf (get-os) "nux"))))))

(defn show
  "Log a value and return that value."
  [x]
  (do (pr x)
      x))