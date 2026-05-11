#!/usr/bin/env bb
;; Validate Clojure code for balanced parentheses using rewrite-clj.
;;
;; Usage:
;;   echo "(defn foo [x] x)" | bb bb/validate.clj
;;   bb bb/validate.clj --file path/to/file.clj
;;   bb bb/validate.clj --code "(defn foo [x] x)"
;;
;; Exit code 0 = valid, 1 = invalid.

(require '[rewrite-clj.parser :as p]
         '[clojure.string :as str]
         '[babashka.fs :as fs])

(defn validate-code
  "Validate that code has balanced parentheses/brackets/braces.
   Uses rewrite-clj parser which understands the full Clojure reader
   grammar — brackets inside strings, regexes, comments etc. are
   handled correctly.
   Returns {:valid true} or {:valid false :error msg}."
  [code source-name]
  (if (str/blank? code)
    {:valid true}
    (try
      ;; parse-string-all parses ALL top-level forms; throws on unbalanced brackets
      (p/parse-string-all code)
      {:valid true}
      (catch Exception e
        {:valid  false
         :error  (.getMessage e)
         :source source-name}))))

(defn validate-file [path]
  (if (fs/exists? path)
    (validate-code (slurp path) (str path))
    {:valid false
     :error  (str "File not found: " path)
     :source (str path)}))

(defn -main [& args]
  (let [opts  (into {} (map (fn [[k v]] [(str/replace k #"^--" "") v])
                            (partition 2 args)))
        result (cond
                 (get opts "file")
                 (validate-file (get opts "file"))

                 (get opts "code")
                 (validate-code (get opts "code") "<arg>")

                 :else
                 (validate-code (slurp *in*) "<stdin>"))]
    (if (:valid result)
      (do (println "OK") (System/exit 0))
      (do (println (str "ERROR: " (:error result)))
          (System/exit 1)))))

(apply -main *command-line-args*)
