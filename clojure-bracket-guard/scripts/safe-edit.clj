#!/usr/bin/env bb
;; Safe edit for Clojure files: validate brackets before writing.
;;
;; Feature-complete replacement for pi's built-in edit tool, plus:
;;   - Bracket validation via rewrite-clj
;;   - Dry-run mode
;;   - Custom separator for --edit specs
;;
;; Single edit:
;;   bb bb/safe-edit.clj --file src/core.clj --old 'old text' --new 'new text'
;;
;; Multiple edits (all matched against original file, applied in reverse order):
;;   bb bb/safe-edit.clj --file src/core.clj \
;;     --edit 'old1>new1' \
;;     --edit 'old2>new2'
;;
;; Or with EDN vector:
;;   bb bb/safe-edit.clj --file src/core.clj \
;;     --edits '[{:old "a" :new "b"} {:old "c" :new "d"}]'
;;
;; Options:
;;   --file <path>      Target Clojure file
;;   --old  <string>    Exact text to find and replace (single edit, backward compat)
;;   --new  <string>    Replacement text (single edit, backward compat)
;;   --edit <spec>      Edit spec "old>new" (repeatable)
;;   --edits <edn>      EDN vector of {:old :new} maps
;;   --dry-run          Only validate, don't write
;;   --sep <char>       Separator for --edit (default: ">")
;;   --format           Format file with clojure-lsp after editing

(require '[rewrite-clj.parser :as p]
         '[clojure.string :as str]
         '[babashka.fs :as fs]
         '[clojure.java.shell :refer [sh]])

;; ============================================================
;; Validation (rewrite-clj)
;; ============================================================

(defn validate-code [code]
  (if (str/blank? code)
    {:valid true}
    (try
      (p/parse-string-all code)
      {:valid true}
      (catch Exception e
        {:valid false
         :error (.getMessage e)}))))

;; ============================================================
;; Normalization (matching pi's edit tool behavior)
;; ============================================================

(defn strip-bom
  "Strip UTF-8 BOM if present. Returns [bom text]."
  [content]
  (if (.startsWith content "\uFEFF")
    ["\uFEFF" (subs content 1)]
    ["" content]))

(defn detect-line-ending
  "Detect the dominant line ending: \\r\\n or \\n."
  [content]
  (let [crlf-idx (.indexOf content "\r\n")
        lf-idx   (.indexOf content "\n")]
    (cond
      (= lf-idx -1) "\n"
      (= crlf-idx -1) "\n"
      (< crlf-idx lf-idx) "\r\n"
      :else "\n")))

(defn normalize-to-lf [text]
  (-> text
      (str/replace #"\r\n" "\n")
      (str/replace #"\r" "\n")))

(defn restore-line-endings [text ending]
  (if (= ending "\r\n")
    (str/replace text #"\n" "\r\n")
    text))

(defn normalize-for-fuzzy
  "Normalize text for fuzzy matching. Handles:
   - Smart single quotes → '
   - Smart double quotes → \"
   - Unicode dashes/hyphens → -
   - Special Unicode spaces → regular space
   - Trailing whitespace per line"
  [text]
  (-> text
      ;; Strip trailing whitespace per line
      (str/split #"\n")
      (->> (map #(str/replace % #"\s+$" ""))
           (str/join "\n"))
      ;; Smart single quotes → '
      (str/replace #"[\u2018\u2019\u201A\u201B]" "'")
      ;; Smart double quotes → "
      (str/replace #"[\u201C\u201D\u201E\u201F]" "\"")
      ;; Unicode dashes/hyphens → -
      (str/replace #"[\u2010\u2011\u2012\u2013\u2014\u2015\u2212]" "-")
      ;; Special spaces → regular space
      (str/replace #"[\u00A0\u2002-\u200A\u202F\u205F\u3000]" " ")))

;; ============================================================
;; Core edit engine (matches pi's edit semantics)
;; ============================================================

(defn fuzzy-find
  "Find old-text in content. Tries exact match first, then fuzzy.
   Returns {:found :index :match-length :used-fuzzy :content-for-replacement}
   or {:found false}."
  [content old-text]
  ;; Try exact match first
  (let [idx (.indexOf content old-text)]
    (if (not= idx -1)
      {:found true :index idx :match-length (count old-text) :used-fuzzy false
       :content-for-replacement content}
      ;; Try fuzzy match
      (let [fuzzy-content (normalize-for-fuzzy content)
            fuzzy-old     (normalize-for-fuzzy old-text)
            fuzzy-idx     (.indexOf fuzzy-content fuzzy-old)]
        (if (not= fuzzy-idx -1)
          {:found true :index fuzzy-idx :match-length (count fuzzy-old)
           :used-fuzzy true :content-for-replacement fuzzy-content}
          {:found false})))))

(defn count-occurrences
  "Count how many times old-text appears in content (fuzzy-aware)."
  [content old-text]
  (let [fc (normalize-for-fuzzy content)
        fo (normalize-for-fuzzy old-text)]
    (loop [idx 0 cnt 0]
      (let [found (.indexOf fc fo idx)]
        (if (= found -1)
          cnt
          (recur (+ found (count fo)) (inc cnt)))))))

(defn apply-edits-pi-style
  "Apply edits matching pi's semantics:
   - All edits matched against the SAME original content
   - Each oldText must be unique (unless --all)
   - Edits must not overlap
   - Applied in reverse order by match position (stable offsets)
   - If any edit needs fuzzy matching, all run in fuzzy-normalized space
   Returns {:ok new-content} or {:error message}."
  [content edits]
  (when (empty? edits)
    (throw (ex-info "No edits provided" {})))

  ;; Check for empty oldText
  (doseq [[i edit] (map-indexed vector edits)]
    (when (empty? (:old edit))
      (throw (ex-info (str "edits[" i "].oldText must not be empty") {}))))

  ;; Find all matches
  (let [initial-matches (mapv #(fuzzy-find content (:old %)) edits)
        any-fuzzy       (some :used-fuzzy initial-matches)
        base-content    (if any-fuzzy
                          (normalize-for-fuzzy content)
                          content)

        ;; Re-find in base content, check uniqueness
        matched-edits
        (loop [i 0 result []]
          (if (= i (count edits))
            result
            (let [edit  (edits i)
                  match (fuzzy-find base-content (:old edit))]
              (if-not (:found match)
                (throw (ex-info (str "Could not find the exact text in file. "
                                     "oldText must match exactly including all whitespace and newlines.\n"
                                     "  edits[" i "]: " (pr-str (subs (:old edit) 0 (min 80 (count (:old edit))))))
                                {:edit-index i}))
                (let [occurrences (count-occurrences base-content (:old edit))]
                  (when (> occurrences 1)
                    (throw (ex-info (str "Found " occurrences " occurrences of edits[" i "] in file. "
                                         "Each oldText must be unique. Please provide more context.")
                                    {:edit-index i :occurrences occurrences})))
                  (recur (inc i)
                         (conj result {:edit-index    i
                                       :match-index   (:index match)
                                       :match-length  (:match-length match)
                                       :new-text      (:new edit)})))))))

        ;; Sort by match position
        sorted (vec (sort-by :match-index matched-edits))]

    ;; Check for overlaps
    (doseq [i (range 1 (count sorted))]
      (let [prev (sorted (dec i))
            curr (sorted i)]
        (when (> (+ (:match-index prev) (:match-length prev)) (:match-index curr))
          (throw (ex-info (str "edits[" (:edit-index prev) "] and edits[" (:edit-index curr)
                               "] overlap. Merge them into one edit or target disjoint regions.")
                          {})))))

    ;; Apply in reverse order (stable offsets)
    (let [new-content
          (reduce
           (fn [text edit]
             (str (subs text 0 (:match-index edit))
                  (:new-text edit)
                  (subs text (+ (:match-index edit) (:match-length edit)))))
           base-content
           (reverse sorted))]

      (if (= base-content new-content)
        (throw (ex-info "No changes made. The replacement produced identical content." {}))
        {:base-content base-content
         :new-content  new-content}))))

;; ============================================================
;; --edit spec parsing
;; ============================================================

(defn parse-edit-specs [specs sep]
  (let [results (mapv
                 (fn [spec]
                   (let [idx (.indexOf ^String spec ^String sep)]
                     (if (neg? idx)
                       {:error (str "Separator '" sep "' not found in edit spec: "
                                    (subs spec 0 (min 60 (count spec))))}
                       {:old (subs spec 0 idx)
                        :new (subs spec (+ idx (count sep)))})))
                 specs)]
    (if-let [err (first (filter :error results))]
      err
      results)))

;; ============================================================
;; Arg parsing
;; ============================================================

(defn parse-args [args]
  (loop [args  args
         opts  {}
         edits []]
    (if (empty? args)
      (assoc opts :edit-specs edits)
      (let [[k & rst] args]
        (cond
          (= k "--file")    (recur (rest rst) (assoc opts :file (first rst)) edits)
          (= k "--old")     (recur (rest rst) (assoc opts :old (first rst)) edits)
          (= k "--new")     (recur (rest rst) (assoc opts :new (first rst)) edits)
          (= k "--edit")    (recur (rest rst) opts (conj edits (first rst)))
          (= k "--edits")   (recur (rest rst) (assoc opts :edits-str (first rst)) edits)
          (= k "--sep")     (recur (rest rst) (assoc opts :sep (first rst)) edits)
          (= k "--dry-run") (recur rst (assoc opts :dry-run true) edits)
          (= k "--format")  (recur rst (assoc opts :format true) edits)
          :else             (recur rst opts edits))))))

;; ============================================================
;; Main
;; ============================================================

(defn -main [& args]
  (let [opts (parse-args args)]

    ;; Validate required args
    (when-not (:file opts)
      (println "ERROR: --file is required") (System/exit 1))
    (when-not (fs/exists? (:file opts))
      (println (str "ERROR: File not found: " (:file opts))) (System/exit 1))

    ;; Build edit list
    (let [edits (cond
                  (:edits-str opts)
                  (try
                    (let [parsed (read-string (:edits-str opts))]
                      (if (and (vector? parsed) (every? #(and (map? %) (contains? % :old)) parsed))
                        parsed
                        (do (println "ERROR: --edits must be an EDN vector of {:old :new} maps")
                            (System/exit 1))))
                    (catch Exception e
                      (println (str "ERROR: failed to parse --edits EDN: " (.getMessage e)))
                      (System/exit 1)))

                  (seq (:edit-specs opts))
                  (let [sep    (or (:sep opts) ">")
                        result (parse-edit-specs (:edit-specs opts) sep)]
                    (if (:error result)
                      (do (println (str "ERROR: " (:error result)))
                          (System/exit 1))
                      result))

                  (and (:old opts) (some? (:new opts)))
                  [{:old (:old opts) :new (:new opts)}]

                  :else
                  (do (println "ERROR: provide --old/--new, --edit, or --edits")
                      (System/exit 1)))

          file    (:file opts)
          raw     (slurp file)

          ;; Normalize: strip BOM, detect line ending, normalize to LF
          [bom content] (strip-bom raw)
          line-ending   (detect-line-ending content)
          normalized    (normalize-to-lf content)]

      ;; Apply edits (pi-style: all against original, reverse order)
      (try
        (let [{:keys [base-content new-content]}
              (apply-edits-pi-style normalized edits)

              ;; Restore BOM and original line endings
              final-content (str bom (restore-line-endings new-content line-ending))]

          ;; Validate with rewrite-clj
          (let [vr (validate-code final-content)]
            (if (:valid vr)
              (if (:dry-run opts)
                (do (println (str "OK: " (count edits) " edit(s) would produce valid Clojure"))
                    (System/exit 0))
                (do (spit file final-content)
                    (when (:format opts)
                      (let [fmt-result (sh "clojure-lsp" "format" "--filenames" file)]
                        (if (zero? (:exit fmt-result))
                          (println (str "OK: " file " updated (" (count edits) " edit(s), formatted)"))
                          (println (str "OK: " file " updated (" (count edits) " edit(s), format failed: " (str/trim (:err fmt-result)) ")")))))
                    (when-not (:format opts)
                      (println (str "OK: " file " updated (" (count edits) " edit(s))")))
                    (System/exit 0)))
              (do (println (str "ERROR: edits would break brackets in " file))
                  (println (str "  " (:error vr)))
                  (println "  File NOT modified.")
                  (System/exit 1)))))

        (catch Exception e
          (println (str "ERROR: " (.getMessage e)))
          (println "  File NOT modified.")
          (System/exit 1))))))

(apply -main *command-line-args*)
