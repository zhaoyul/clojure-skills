(ns main
  "CLI entry point for clj-struct-edit Phase 2.
   Usage: echo '{\"op\":\"snapshot\",\"file\":\"...\"}' | bb main.clj"
  (:require [core :as c]
            [lsp :as l]
            [clojure.string :as str]
            [cheshire.core :as json]))

;; ─── JSON Protocol ───

(defn- read-json
  "Read JSON from stdin."
  []
  (json/parse-string (slurp *in*) true))

(defn- write-json
  "Write JSON to stdout."
  [data]
  (println (json/generate-string data)))

(defn- find-project-root
  "Auto-detect project root by looking for git repo or deps.edn."
  []
  (let [cwd (.getCanonicalPath (java.io.File. "."))]
    (loop [dir (java.io.File. cwd)]
      (if (nil? dir)
        cwd
        (let [parent (.getParentFile dir)
              dir-name (.getName dir)
              has-git (.exists (java.io.File. dir ".git"))
              has-deps (.exists (java.io.File. dir "deps.edn"))
              has-bb (.exists (java.io.File. dir "bb.edn"))]
          (cond
            has-git (.getCanonicalPath dir)
            has-deps (.getCanonicalPath dir)
            :else (recur parent)))))))

(defn- err
  "Build error response."
  [message]
  {:ok false :error message})

;; ─── Validation ───

(defn- inside-project?
  "Check if file is inside project root."
  [project-root file-path]
  (let [abs-file (.getCanonicalPath (java.io.File. file-path))
        abs-root (.getCanonicalPath (java.io.File. project-root))]
    (str/starts-with? abs-file abs-root)))

(defn- check-parens
  "Validate parentheses balance."
  [text]
  (try
    (rewrite-clj.parser/parse-string-all text)
    true
    (catch Exception e
      false)))

;; ─── File I/O ───

(defn- resolve-path
  "Resolve file path relative to project-root if relative."
  [project-root path]
  (let [f (java.io.File. path)]
    (if (.isAbsolute f)
      (.getCanonicalPath f)
      (.getCanonicalPath (java.io.File. project-root path)))))

(defn- read-file
  [path]
  (slurp path :encoding "UTF-8"))

(defn- write-file
  [path content]
  (spit path content :encoding "UTF-8"))

;; ─── Diff ───

(defn- make-diff
  "Generate unified diff of old vs new text."
  [old-text new-text file-path]
  (let [old-file (java.io.File/createTempFile "old" ".clj")
        new-file (java.io.File/createTempFile "new" ".clj")]
    (try
      (spit old-file old-text)
      (spit new-file new-text)
      (let [result (clojure.java.shell/sh "diff" "-u"
                                          (.getAbsolutePath old-file)
                                          (.getAbsolutePath new-file))]
        (if (= 0 (:exit result))
          ""
          (:out result)))
      (finally
        (.delete old-file)
        (.delete new-file)))))

;; ─── Operation Dispatch ───

(defn- dispatch-op
  "Dispatch operation and return result."
  [op project-root]
  (let [file-path (when (:file op) (resolve-path project-root (:file op)))
        operation (:op op)
        save-flag (get op :save false)
        target (:target op)
        old-text (when file-path (read-file file-path))]
    (try
      ;; Security check
      (when file-path
        (when-not (inside-project? project-root file-path)
          (throw (ex-info "File outside project root" {:file file-path}))))

      ;; Execute
      (let [result
            (case operation
              "snapshot"
              (c/snapshot old-text)

              "get-form"
              (let [zloc (c/zip-safe old-text)
                    target-zloc (c/resolve-target zloc target)]
                (if target-zloc
                  {:ok true
                   :text (rewrite-clj.node/string (rewrite-clj.zip/node target-zloc))
                   :range {:start (c/node-position target-zloc)
                           :end (c/node-end-position target-zloc)}
                   :sha256 (c/node-hash target-zloc)}
                  (err "Target not found")))

              "replace-form"
              (let [new-form (:new_form op)
                    result (c/replace-form old-text target new-form)]
                (if (check-parens (:text result))
                  (assoc result :ok true)
                  (err "check-parens failed after replacement")))

              "insert-form-after"
              (let [new-form (:new_form op)
                    result (c/insert-form-after old-text target new-form)]
                (if (check-parens (:text result))
                  (assoc result :ok true)
                  (err "check-parens failed after insertion")))

              "insert-form-before"
              (let [new-form (:new_form op)
                    result (c/insert-form-before old-text target new-form)]
                (if (check-parens (:text result))
                  (assoc result :ok true)
                  (err "check-parens failed after insertion")))

              "remove-form"
              (let [result (c/remove-form old-text target)]
                (if (check-parens (:text result))
                  (assoc result :ok true)
                  (err "check-parens failed after removal")))

              "wrap-form"
              (let [wrapper (:wrapper op)
                    result (c/wrap-form old-text target wrapper)]
                (if (check-parens (:text result))
                  (assoc result :ok true)
                  (err "check-parens failed after wrap")))

              "splice-form"
              (let [result (c/splice-form old-text target)]
                (if (check-parens (:text result))
                  (assoc result :ok true)
                  (err "check-parens failed after splice")))

              "add-require"
              (let [require-spec (:require op)
                    result (c/add-require old-text require-spec)]
                (if (check-parens (:text result))
                  (assoc result :ok true)
                  (err "check-parens failed after add-require")))

              "rename-symbol"
              (l/rename-symbol project-root
                               (:namespace op)
                               (:symbol op)
                               (:new_name op))

              "clean-ns"
              (l/clean-ns project-root file-path)

              "find-references"
              (l/find-references project-root
                                 (:namespace op)
                                 (:symbol op))

              "validate"
              (c/validate-file old-text)

              (err (str "Unsupported op: " operation)))]

        ;; Save if requested
        (if (and save-flag (:text result) file-path)
          (do
            (write-file file-path (:text result))
            (let [diff (make-diff old-text (:text result) file-path)]
              (assoc result
                     :saved true
                     :diff (if (str/blank? diff) nil diff))))
          (if (:text result)
            (assoc result :dry_run true)
            result)))

      (catch Exception e
        (err (str (.getMessage e)))))))

;; ─── Entry Point ───

(defn -main
  [& _]
  (let [op (read-json)
        project-root (or (:project_root op)
                         (System/getenv "PROJECT_ROOT")
                         (find-project-root))]
    (write-json (dispatch-op op project-root))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
