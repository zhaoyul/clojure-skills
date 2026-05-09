(ns lsp
  "Clojure-lsp integration for semantic refactoring."
  (:require [clojure.string :as str]
            [clojure.java.shell :as shell]))

(defn- run-clojure-lsp
  "Run clojure-lsp command in project root."
  [project-root & args]
  (let [result (apply shell/sh
                      "clojure-lsp"
                      (concat args
                              [:dir project-root]))]
    (if (= 0 (:exit result))
      {:ok true :output (:out result) :err (:err result)}
      {:ok false :error (:err result) :output (:out result) :exit (:exit result)})))

(defn clean-ns
  "Clean namespace: sort requires, remove unused."
  [project-root file-path]
  (let [result (run-clojure-lsp project-root
                                "clean-ns"
                                "--filename" file-path)]
    (if (:ok result)
      {:ok true
       :action :clean-ns
       :file file-path
       :message (if (str/blank? (:output result))
                  "Namespace cleaned"
                  (:output result))}
      {:ok false
       :error (:error result)})))

(defn rename-symbol
  "Rename symbol across project."
  [project-root ns-name sym-name new-name]
  (let [from (str ns-name "/" sym-name)
        result (run-clojure-lsp project-root
                                "rename"
                                "--from" from
                                "--to" new-name)]
    (if (:ok result)
      {:ok true
       :action :rename
       :from from
       :to new-name
       :message (:output result)}
      {:ok false
       :error (:error result)})))

(defn find-references
  "Find references to a symbol."
  [project-root ns-name sym-name]
  (let [result (run-clojure-lsp project-root
                                "find-references"
                                "--ns" ns-name
                                "--name" sym-name)]
    (if (:ok result)
      (let [lines (str/split-lines (:output result))
            refs (keep (fn [line]
                         (when (re-matches #".*:\d+:\d+" line)
                           (let [[path row col] (str/split line #":")]
                             {:file path
                              :line (parse-long row)
                              :col (parse-long col)})))
                       lines)]
        {:ok true
         :symbol (str ns-name "/" sym-name)
         :references refs
         :count (count refs)})
      {:ok false
       :error (:error result)})))

(defn diagnostics
  "Run clojure-lsp diagnostics on file."
  [project-root file-path]
  (let [result (run-clojure-lsp project-root
                                "diagnostics"
                                "--filename" file-path)]
    (if (:ok result)
      {:ok true
       :file file-path
       :diagnostics (:output result)}
      {:ok false
       :error (:error result)})))
