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
;;   --target <node-id>  Parse file via rewrite-clj, find form at "row:col" position,
;;                       and replace entire form with --new text.
;;                       This avoids shell-encoding issues with special characters
;;                       in --old, at the cost of replacing the whole enclosing form.

(require '[rewrite-clj.parser :as p]
         '[rewrite-clj.zip :as z]
         '[rewrite-clj.node :as n]
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

;; ─── AST-based target editing (bypasses shell-encoding issues in --old) ───

(defn node-position
  "Return [row col] of zipper node (1-based)."
  [zloc]
  (when zloc
    (vec (z/position zloc))))

(defn node-end-position
  "Return [end-row end-col]."
  [zloc]
  (when zloc
    (let [[[_ _] [er ec]] (z/position-span zloc)]
      [er ec])))

(defn find-zloc-by-position
  "Find the deepest zloc at or containing [target-row target-col]."
  [zloc [target-row target-col]]
  (loop [z zloc]
    (if-not z
      nil
      (let [[r c] (node-position z)
            [er ec] (node-end-position z)]
        (cond
          ;; Exact match at start position
          (and (= r target-row) (= c target-col))
          z
          ;; Target is before this node, skip right
          (or (< target-row r)
              (and (= target-row r) (< target-col c)))
          (recur (z/right z))
          ;; Target is after this node, skip right
          (or (> target-row er)
              (and (= target-row er) (> target-col ec)))
          (recur (z/right z))
          ;; Target is inside this node, go deeper
          :else
          (if-let [child (z/down z)]
            (or (find-zloc-by-position child [target-row target-col])
                z)
            z))))))

(defn node-text
  "Get full text of the node at zloc."
  [zloc]
  (n/string (z/node zloc)))

(defn replace-by-target
  "Parse file, find form at node-id (\"row:col\"), replace it with new-form.
   Returns {:old_text ... :new_text ... :old_range ...} or throws."
  [content target-id new-form-text]
  (let [[row col] (map parse-long (str/split target-id #":"))
        zloc (z/of-string content {:track-position? true})
        target-zloc (find-zloc-by-position zloc [row col])]
    (when-not target-zloc
      (throw (ex-info (str "Target not found at position " target-id) {})))
    (let [old-text (node-text target-zloc)
          new-node (p/parse-string-all new-form-text)
          replaced (z/replace target-zloc new-node)
          new-content (z/root-string replaced)]
      {:old_text old-text
       :new_text new-form-text
       :old_range [(node-position target-zloc) (node-end-position target-zloc)]
       :base-content content
       :new-content new-content})))

;; ─── Child operations (insert/remove/swap) ───

(defn- list-children
  "Get all child nodes of target as a vector."
  [target-zloc]
  (loop [c (z/down target-zloc) acc []]
    (if c
      (recur (z/right c) (conj acc (z/node c)))
      acc)))

(defn swap-children
  "Swap two children at indices a and b within the target form.
   Preserves original whitespace/formatting by using node positions.
   Indices are 0-based (0 = defn, 1 = name, 2 = first arg, etc.)"
  [content target-id idx-a idx-b]
  (let [[row col] (map parse-long (str/split target-id #":"))
        zloc (z/of-string content {:track-position? true})
        target-zloc (find-zloc-by-position zloc [row col])]
    (when-not target-zloc
      (throw (ex-info (str "Target not found at " target-id) {})))
    (let [old-text (node-text target-zloc)
          a-min (min idx-a idx-b)
          b-max (max idx-a idx-b)]
      ;; Walk children to get their source positions
      (let [child-data (loop [c (z/down target-zloc) idx 0 acc []]
                         (if c
                           (let [start-pos (z/position c)
                                 end-pos (second (z/position-span c))
                                 node (z/node c)]
                             (recur (z/right c) (inc idx)
                                    (conj acc {:node node
                                               :start start-pos
                                               :end end-pos
                                               :text (n/string node)
                                               :idx idx})))
                           acc))
            a-data (nth child-data a-min)
            b-data (nth child-data b-max)]
        (when (or (nil? a-data) (nil? b-data))
          (throw (ex-info (str "Child index out of range: " idx-a " " idx-b) {})))
        ;; Convert positions to character offsets
        (let [lines (str/split-lines content)
              pos->offset (fn [[r c]]
                            (+ (dec c)
                               (reduce + 0 (map #(+ (count %) 1)
                                               (take (dec r) lines)))))
              a-start (pos->offset (:start a-data))
              a-end (pos->offset (:end a-data))
              b-start (pos->offset (:start b-data))
              b-end (pos->offset (:end b-data))
              ;; Extract gaps from source
              form-start (pos->offset (z/position target-zloc))
              form-end (pos->offset (second (z/position-span target-zloc)))
              form-text (subs content form-start form-end)
              ;; Adjust offsets to be relative to form-text
              rel-a-start (- a-start form-start)
              rel-a-end (- a-end form-start)
              rel-b-start (- b-start form-start)
              rel-b-end (- b-end form-start)
              a-gap-start (min rel-a-start rel-b-start)
              b-gap-end (max rel-a-end rel-b-end)
              ;; Which child comes first in source?
              first-is-a (< rel-a-start rel-b-start)
              ;; Gaps based on source order
              gap1 (subs form-text 0 (if first-is-a rel-a-start rel-b-start))
              mid (subs form-text (if first-is-a rel-a-end rel-b-end) (if first-is-a rel-b-start rel-a-start))
              gap2 (subs form-text (if first-is-a rel-b-end rel-a-end))
              ;; Rebuild with swapped nodes
              new-form-text (str gap1
                                (if first-is-a (:text b-data) (:text a-data))
                                mid
                                (if first-is-a (:text a-data) (:text b-data))
                                gap2)
              new-node (p/parse-string-all new-form-text)
              replaced (z/replace target-zloc new-node)]
          {:old_text old-text
           :new_text (z/root-string replaced)
           :base-content content
           :new-content (z/root-string replaced)})))))

(defn remove-child
  "Remove child at index from the target form."
  [content target-id idx]
  (let [[row col] (map parse-long (str/split target-id #":"))
        zloc (z/of-string content {:track-position? true})
        target-zloc (find-zloc-by-position zloc [row col])
        children (list-children target-zloc)]
    (when-not target-zloc
      (throw (ex-info (str "Target not found at " target-id) {})))
    (let [old-text (node-text target-zloc)
          kept (keep-indexed (fn [i v] (when (not= i idx) v)) children)
          new-target-str (str "(" (str/join " " (map n/string kept)) ")")
          new-node (p/parse-string-all new-target-str)
          replaced (z/replace target-zloc new-node)]
      {:old_text old-text
       :new_text (z/root-string replaced)
       :base-content content
       :new-content (z/root-string replaced)})))



(defn splice-form
  "Splice (unparent) the target form: replace its parent list with the target itself.
   E.g., (foo (bar (target)))  with target at (target) -> (foo (target))
   Useful for removing unnecessary wrappers like (clj->js ...)."
  [content target-id]
  (let [[row col] (map parse-long (str/split target-id #":"))
        zloc (z/of-string content {:track-position? true})
        target-zloc (find-zloc-by-position zloc [row col])]
    (when-not target-zloc
      (throw (ex-info (str "Target not found at " target-id) {})))
    (let [parent (z/up target-zloc)]
      (when-not parent
        (throw (ex-info "Cannot splice: target has no parent" {})))
      (let [old-parent-text (node-text parent)
            node (z/node target-zloc)
            replaced (z/replace parent node)]
        {:old_text old-parent-text
         :new_text (z/root-string replaced)
         :base-content content
         :new-content (z/root-string replaced)}))))

(defn wrap-form
  "Wrap target form in a wrapper template.
   Wrapper must contain exactly one %FORM% placeholder."
  [content target-id wrapper-text]
  (when-not (str/includes? wrapper-text "%FORM%")
    (throw (ex-info "Wrapper must contain %FORM% placeholder" {})))
  (let [[row col] (map parse-long (str/split target-id #":"))
        zloc (z/of-string content {:track-position? true})
        target-zloc (find-zloc-by-position zloc [row col])]
    (when-not target-zloc
      (throw (ex-info (str "Target not found at " target-id) {})))
    (let [old-text (node-text target-zloc)
          full-text (str/replace wrapper-text "%FORM%" old-text)
          new-node (p/parse-string full-text)
          replaced (z/replace target-zloc new-node)]
      {:old_text old-text
       :new_text (z/root-string replaced)
       :base-content content
       :new-content (z/root-string replaced)})))
(defn unwrap-form
  "Unwrap a wrapper form: replace (fn arg ...) with its first child after fn.
   Useful for removing (clj->js ...) wrappers: target clj->js, unwrap keeps next child.
   Unlike splice which goes UP to parent, unwrap stays at the same level
   and removes the outer form, keeping the inner content."
  [content target-id]
  (let [[row col] (map parse-long (str/split target-id #":"))
        zloc (z/of-string content)
        ;; Find the form by looking for the symbol at target position
        ;; Use rewrite-clj's tree walk
        target-zloc (z/find-value zloc z/next (symbol (str "?")))]  ;; placeholder
    ;; Actually, target-id won't work for position-based. Instead:
    ;; Find by navigating the AST
    nil))

(defn unwrap-by-symbol
  "Find a form by its first child symbol and unwrap it.
   E.g., find (clj->js ...) and replace it with its inner content.
   Uses z/find-value to locate, bypassing position tracking issues in babashka."
  [content sym-str]
  (let [sym (symbol sym-str)
        zloc (z/of-string content)
        found (z/find-value zloc z/next sym)]
    (if-not found
      (throw (ex-info (str "Symbol not found: " sym-str) {}))
      (let [parent (z/up found)]
        (if-not parent
          (throw (ex-info (str sym-str " has no parent to unwrap") {}))
          (let [inner (-> parent z/down z/right)]
            (if-not inner
              (throw (ex-info (str sym-str " wrapper has no content") {}))
              (let [old-text (n/string (z/node parent))
                    replaced (z/replace parent (z/node inner))]
                {:old_text old-text
                 :new_text (z/root-string replaced)
                 :base-content content
                 :new-content (z/root-string replaced)}))))))))

(defn insert-child
  "Insert new-form-text as child at index in the target form."
  [content target-id idx new-form-text]
  (let [[row col] (map parse-long (str/split target-id #":"))
        zloc (z/of-string content {:track-position? true})
        target-zloc (find-zloc-by-position zloc [row col])
        children (list-children target-zloc)]
    (when-not target-zloc
      (throw (ex-info (str "Target not found at " target-id) {})))
    (let [old-text (node-text target-zloc)
          new-node (p/parse-string new-form-text)
          new-children (-> (vec children) (vec) (#(if (>= idx (count %)) (conj % new-node) (into [] (concat (subvec % 0 idx) [new-node] (subvec % idx))))))
          new-target-str (str "(" (str/join " " (map n/string new-children)) ")")
          parsed (p/parse-string-all new-target-str)
          replaced (z/replace target-zloc parsed)]
      {:old_text old-text
       :new_text (z/root-string replaced)
       :base-content content
       :new-content (z/root-string replaced)})))

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
   - Each oldText must be unique (unless :replace-all is true)
   - Edits must not overlap
   - Applied in reverse order by match position (stable offsets)
   - If any edit needs fuzzy matching, all run in fuzzy-normalized space
   Returns {:ok new-content} or {:error message}."
  [content edits & {:keys [replace-all]}]
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
                  (when (and (> occurrences 1) (not replace-all))
                    (throw (ex-info (str "Found " occurrences " occurrences of edits[" i "] in file. "
                                         "Each oldText must be unique (or use --replace-all).")
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

(defn replace-all
  "Replace ALL occurrences of each old->new mapping.
   Unlike apply-edits-pi-style, this does global string replacement
   (not position-based). Skips uniqueness check entirely.
   Useful for batch refactoring (e.g. color constants)."
  [content edits]
  (let [result (reduce (fn [txt {:keys [old new]}]
                         (str/replace txt old new))
                       content
                       edits)]
    (if (= content result)
      (throw (ex-info "No changes made." {}))
      {:base-content content
       :new-content  result})))

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
          (= k "--target")  (recur (rest rst) (assoc opts :target (first rst)) edits)
          (= k "--op")      (recur (rest rst) (assoc opts :op (keyword (first rst))) edits)
          (= k "--swap")    (recur (rest rst) (assoc opts :swap (first rst)) edits)
          (= k "--idx")     (recur (rest rst) (assoc opts :idx (first rst)) edits)
          (= k "--wrap")    (recur (rest rst) (assoc opts :wrap (first rst)) edits)
          (= k "--replace-all") (recur rst (assoc opts :replace-all true) edits)
          (= k "--mapping-file") (recur (rest rst) (assoc opts :mapping-file (first rst)) edits)
          (= k "--unwrap-symbol") (recur (rest rst) (assoc opts :unwrap-symbol (first rst)) edits)
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

                  (:target opts)
                  nil  ; handled below, not text-based

                  (:mapping-file opts)
                  []  ; handled below, reads from file

                  (:unwrap-symbol opts)
                  []  ; handled below

                  :else
                  (do (println "ERROR: provide --old/--new, --edit, --edits, or --target")
                      (System/exit 1)))

          target  (:target opts)
          file    (:file opts)
          raw     (slurp file)

          ;; Normalize: strip BOM, detect line ending, normalize to LF
          [bom content] (strip-bom raw)
          line-ending   (detect-line-ending content)
          normalized    (normalize-to-lf content)]

      ;; Apply edits (pi-style: all against original, reverse order)
      ;; OR target-based (AST replacement by position)
      (try
        (if target
          (let [op        (:op opts)
                new-text  (:new opts)
                swap-idxs (when (:swap opts) (map parse-long (str/split (:swap opts) #",")))
                idx       (when (:idx opts) (parse-long (:idx opts)))

                result
                (case op
                  :swap (swap-children normalized target (first swap-idxs) (second swap-idxs))
                  :remove (remove-child normalized target idx)
                  :insert (insert-child normalized target idx new-text)
                  :splice (splice-form normalized target)
                  :wrap (wrap-form normalized target new-text)
                  ;; default: replace entire form
                  (replace-by-target normalized target new-text))

                new-content (:new-content result)
                final-content (str bom (restore-line-endings new-content line-ending))]
            (let [vr (validate-code final-content)]
              (if (:valid vr)
                (if (:dry-run opts)
                  (do (println (str "OK: target " target " would replace " (count (:old_text result)) " chars"))
                      (System/exit 0))
                  (do (spit file final-content)
                      (when (:format opts)
                        (let [fmt-result (sh "clojure-lsp" "format" "--filenames" file)]
                          (if (zero? (:exit fmt-result))
                            (println (str "OK: " file " updated (target " target ", formatted)"))
                            (println (str "OK: " file " updated (target " target ", format failed: " (str/trim (:err fmt-result)) ")")))))
                      (when-not (:format opts)
                        (println (str "OK: " file " updated (target " target ", replaced " (count (:old_text result)) " chars)")))
                      (System/exit 0)))
                (do (println (str "ERROR: target edit would break brackets in " file))
                    (println (str "  " (:error vr)))
                    (println "  File NOT modified.")
                    (System/exit 1))))))

          ;; ─── Text-based edit ───
          (let [result (cond
                        (:mapping-file opts)
                        (let [map-path (:mapping-file opts)
                              lines (str/split-lines (slurp map-path))
                              map-edits (mapv (fn [line]
                                               (let [[_ old new] (re-matches #"([^|]+)\|\|([^|]+)" line)]
                                                 (when (and old new) {:old old :new new})))
                                              lines)
                              valid (remove nil? map-edits)]
                          (if (empty? valid)
                            (do (println "ERROR: no valid mappings found in " map-path)
                                (System/exit 1))
                            (replace-all normalized valid)))

                        (:replace-all opts)
                        (replace-all normalized edits)

                        (:unwrap-symbol opts)
                        (unwrap-by-symbol normalized (:unwrap-symbol opts))

                        :else
                        (apply-edits-pi-style normalized edits))
                new-content (:new-content result)
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
                          (println (str "OK: " file " updated" (when (:mapping-file opts) (str " (mapping: " (:mapping-file opts) ")")) (when (and (not (:mapping-file opts)) (seq edits)) (str " (" (count edits) " edit(s))")) ", formatted)"))
                          (println (str "OK: " file " updated" (when (:mapping-file opts) (str " (mapping: " (:mapping-file opts) ")")) (when (and (not (:mapping-file opts)) (seq edits)) (str " (" (count edits) " edit(s))")) ", format failed: " (str/trim (:err fmt-result)) ")")))))
                    (when-not (:format opts)
                      (println (str "OK: " file " updated" (when (:mapping-file opts) (str " (mapping: " (:mapping-file opts) ")")) (when (and (not (:mapping-file opts)) (seq edits)) (str " (" (count edits) " edit(s))")))))
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
