(ns core
  "Clojure structural editing core using rewrite-clj.
   Phase 2: snapshot, replace-form, add-require, wrap-form, splice-form."
  (:require [rewrite-clj.parser :as p]
            [rewrite-clj.zip :as z]
            [rewrite-clj.node :as n]
            [clojure.string :as str]))

;; ─── Utilities ───

(defn zip-safe
  "Create a zipper from text, preserving comments and whitespace."
  [text]
  (z/of-string text {:track-position? true}))

(defn node-position
  "Return [row col] of the current zipper node."
  [zloc]
  (when zloc
    (vec (z/position zloc))))

(defn node-end-position
  "Return [end-row end-col] of the current zipper node."
  [zloc]
  (when zloc
    (let [[[sr sc] [er ec]] (z/position-span zloc)]
      [er ec])))

(defn node-hash
  "SHA256 of node text."
  [zloc]
  (when zloc
    (let [text (n/string (z/node zloc))
          bytes (.getBytes text "UTF-8")
          md (java.security.MessageDigest/getInstance "SHA-256")]
      (.update md bytes)
      (->> (.digest md)
           (map #(format "%02x" %))
           (str/join)))))

(defn node-preview
  "Short preview of node text."
  [zloc max-len]
  (when zloc
    (let [text (n/string (z/node zloc))]
      (if (> (count text) max-len)
        (str (subs text 0 (- max-len 3)) "...")
        text))))

(defn node-kind
  "Return kind tag of node: :ns, :def, :defn, :defmacro, :list, :vector, etc."
  [zloc]
  (when zloc
    (let [tag (n/tag (z/node zloc))]
      (if (= tag :list)
        (let [first-child (-> zloc z/down)]
          (when first-child
            (let [sym (z/sexpr first-child)]
              (cond
                (= sym 'ns) :ns
                (= sym 'def) :def
                (= sym 'defn) :defn
                (= sym 'defn-) :defn
                (= sym 'defmacro) :defmacro
                (= sym 'defmulti) :defmulti
                (= sym 'defmethod) :defmethod
                (= sym 'defprotocol) :defprotocol
                (= sym 'defrecord) :defrecord
                (= sym 'deftype) :deftype
                :else :list))))
        tag))))

(defn node-name
  "Return the name symbol for def/defn/ns forms."
  [zloc]
  (when zloc
    (let [tag (node-kind zloc)]
      (when (#{:ns :def :defn :defmacro :defmulti :defprotocol :defrecord :deftype} tag)
        (let [name-node (-> zloc z/down z/right)]
          (when name-node
            (str (z/sexpr name-node))))))))

;; ─── Node ID / Path ───

(defn find-zloc-by-position
  "Find the zloc at or containing [row col]."
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
          ;; Target is before this node, skip
          (or (< target-row r)
              (and (= target-row r) (< target-col c)))
          (recur (z/right z))
          ;; Target is after this node, skip
          (or (> target-row er)
              (and (= target-row er) (> target-col ec)))
          (recur (z/right z))
          ;; Target is inside this node
          :else
          (if-let [child (z/down z)]
            (or (find-zloc-by-position child [target-row target-col])
                z)
            z))))))

;; ─── Snapshot ───

(defn- collect-forms
  "Collect all top-level and nested named forms from zipper."
  [zloc max-depth]
  (let [results (atom [])]
    (loop [z zloc]
      (when z
        (let [kind (node-kind z)]
          (when (and kind
                     (#{:ns :def :defn :defmacro :defmulti
                        :defmethod :defprotocol :defrecord :deftype} kind))
            (swap! results conj
                   {:node_id (str (first (node-position z)) ":" (second (node-position z)))
                    :kind (name kind)
                    :name (node-name z)
                    :range {:start (node-position z)
                            :end (node-end-position z)}
                    :sha256 (node-hash z)
                    :preview (node-preview z 120)
                    :depth (if (= kind :ns) 0 1)}))
          (recur (z/right z)))))
    @results))

(defn snapshot
  "Return snapshot of file with rewrite-clj AST info."
  [text]
  (let [zloc (zip-safe text)]
    {:ok true
     :forms (collect-forms zloc 2)
     :line_count (count (str/split-lines text))}))

;; ─── Form Operations ───

(defn resolve-target
  "Find zloc by target spec. Supports :node_id or :range."
  [zloc target]
  (cond
    (:node_id target)
    (let [[row col] (map parse-long (str/split (:node_id target) #":"))]
      (find-zloc-by-position zloc [row col]))

    (:range target)
    (let [[[row col]] (:range target)]
      (find-zloc-by-position zloc [row col]))

    :else nil))

(defn verify-hash
  "Verify zloc hash matches expected sha256."
  [zloc expected]
  (when expected
    (let [actual (node-hash zloc)]
      (when (not= expected actual)
        (throw (ex-info "Target hash mismatch"
                        {:expected expected :actual actual}))))))

(defn replace-form
  "Replace form at target with new-form text."
  [text target new-form-text]
  (let [zloc (zip-safe text)
        target-zloc (resolve-target zloc target)]
    (when-not target-zloc
      (throw (ex-info "Target not found" {:target target})))
    (verify-hash target-zloc (:sha256 target))
    (let [new-node (p/parse-string-all new-form-text)
          replaced (z/replace target-zloc new-node)]
      {:text (z/root-string replaced)
       :old_sha256 (node-hash target-zloc)
       :new_sha256 (-> replaced node-hash)})))

(defn insert-form-after
  "Insert new-form after target."
  [text target new-form-text]
  (let [zloc (zip-safe text)
        target-zloc (resolve-target zloc target)]
    (when-not target-zloc
      (throw (ex-info "Target not found" {:target target})))
    (let [new-node (p/parse-string new-form-text)
          inserted (z/insert-right target-zloc new-node)]
      {:text (z/root-string inserted)})))

(defn insert-form-before
  "Insert new-form before target."
  [text target new-form-text]
  (let [zloc (zip-safe text)
        target-zloc (resolve-target zloc target)]
    (when-not target-zloc
      (throw (ex-info "Target not found" {:target target})))
    (let [new-node (p/parse-string new-form-text)
          inserted (z/insert-left target-zloc new-node)]
      {:text (z/root-string inserted)})))

(defn remove-form
  "Remove form at target."
  [text target]
  (let [zloc (zip-safe text)
        target-zloc (resolve-target zloc target)]
    (when-not target-zloc
      (throw (ex-info "Target not found" {:target target})))
    (verify-hash target-zloc (:sha256 target))
    (let [removed (z/remove* target-zloc)]
      {:text (z/root-string removed)
       :old_sha256 (node-hash target-zloc)})))

;; ─── wrap-form ───

(defn wrap-form
  "Wrap target form in a wrapper template.
   Wrapper must contain exactly one %FORM% placeholder."
  [text target wrapper-text]
  (when-not (str/includes? wrapper-text "%FORM%")
    (throw (ex-info "Wrapper must contain %FORM% placeholder" {})))
  (let [zloc (zip-safe text)
        target-zloc (resolve-target zloc target)]
    (when-not target-zloc
      (throw (ex-info "Target not found" {:target target})))
    (let [target-text (n/string (z/node target-zloc))
          full-text (str/replace wrapper-text "%FORM%" target-text)
          new-node (p/parse-string full-text)
          replaced (z/replace target-zloc new-node)]
      {:text (z/root-string replaced)})))

;; ─── splice-form ───

(defn splice-form
  "Remove outer list around target, keeping target itself.
   E.g. (wrapper (target)) -> (target)"
  [text target]
  (let [zloc (zip-safe text)
        target-zloc (resolve-target zloc target)]
    (when-not target-zloc
      (throw (ex-info "Target not found" {:target target})))
    (if-let [up (z/up target-zloc)]
      (let [replaced (z/replace up (z/node target-zloc))]
        {:text (z/root-string replaced)})
      (throw (ex-info "Cannot splice: no parent list" {})))))

;; ─── add-require ───

(defn- find-ns-form
  "Find the ns form zipper."
  [zloc]
  (z/find-value zloc z/next 'ns))

(defn- find-require-vec
  "Find the (:require ...) list inside ns form."
  [ns-zloc]
  (z/find-next-depth-first ns-zloc
    #(when (= :list (n/tag (z/node %)))
       (let [first-child (z/down %)]
         (and first-child (= :require (z/sexpr first-child)))))))

(defn add-require
  "Add a require to the ns form.
   require-spec: {:namespace 'clojure.string' :alias 'str'}"
  [text require-spec]
  (let [zloc (zip-safe text)
        ns-zloc (find-ns-form zloc)]
    (when-not ns-zloc
      (throw (ex-info "No ns form found" {})))
    (let [require-list (find-require-vec ns-zloc)]
      (if require-list
        ;; Add to existing require list
        (let [new-require (if (:alias require-spec)
                            (format "[%s :as %s]" (:namespace require-spec) (:alias require-spec))
                            (format "[%s]" (:namespace require-spec)))
              new-node (p/parse-string new-require)
              inserted (z/append-child require-list new-node)
              edited (z/up inserted)]
          {:text (z/root-string edited)})
        ;; Create new require list
        (let [new-require (if (:alias require-spec)
                            (format "(:require [%s :as %s])" (:namespace require-spec) (:alias require-spec))
                            (format "(:require [%s])" (:namespace require-spec)))
              new-node (p/parse-string new-require)
              after-name (-> ns-zloc z/down z/right)]
          (if after-name
            (let [inserted (z/insert-right after-name new-node)]
              {:text (z/root-string inserted)})
            (throw (ex-info "Could not find insertion point in ns form" {}))))))))

;; ─── Validation ───

(defn validate-file
  "Validate Clojure syntax by trying to parse."
  [text]
  (try
    (p/parse-string-all text)
    {:ok true :check_parse "ok"}
    (catch Exception e
      {:ok false :error (str "Parse error: " (.getMessage e))})))
