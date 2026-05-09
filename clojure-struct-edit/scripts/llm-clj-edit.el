;;; llm-clj-edit.el --- Safe structural editing for Clojure via emacsclient -*- lexical-binding: t; -*-
;; Phase 1 MVP: snapshot, get-form, replace-form, validate
;;
;; Usage: emacsclient -a "" --eval \
;;        '(llm-clj-edit-apply-file "/tmp/op.json" "/tmp/result.json")'

(require 'json)
(require 'cl-lib)

(defvar llm-clj-edit-project-root nil)

(defun llm-clj-edit--read-file (path)
  (with-temp-buffer
    (insert-file-contents path)
    (buffer-string)))

(defun llm-clj-edit--write-file (path content)
  (with-temp-file path
    (insert content)))

(defun llm-clj-edit--inside-project-p (file)
  (let* ((root (file-truename (or llm-clj-edit-project-root default-directory)))
         (path (file-truename (expand-file-name file))))
    (string-prefix-p root path)))

(defun llm-clj-edit--ensure-clojure-mode (_file)
  (cond
   ((derived-mode-p 'clojure-mode 'clojure-ts-mode 'clojurec-mode 'clojurescript-mode))
   ((fboundp 'clojure-ts-mode) (clojure-ts-mode))
   ((fboundp 'clojure-mode) (clojure-mode))
   (t (lisp-mode))))

(defun llm-clj-edit--goto-line-col (line col)
  (goto-char (point-min))
  (forward-line (1- line))
  (when (and col (> col 0))
    (move-to-column (1- col))))

(defun llm-clj-edit--bounds-of-sexp-at-point ()
  (or (bounds-of-thing-at-point 'sexp)
      (save-excursion
        (let ((beg (condition-case nil (progn (backward-up-list) (point)) (error nil))))
          (when beg (forward-sexp) (cons beg (point)))))))

(defun llm-clj-edit--sha256 (text)
  (secure-hash 'sha256 text))

(defun llm-clj-edit--form-at-target (target)
  (let* ((range (cdr (assoc 'range target)))
         (start (and range (elt range 0)))
         (line (and start (elt start 0)))
         (col (and start (elt start 1))))
    (unless (and line col)
      (error "Target range [line, col] is required"))
    (llm-clj-edit--goto-line-col line col)
    (let ((bounds (llm-clj-edit--bounds-of-sexp-at-point)))
      (unless bounds
        (error "No sexp found at target line %d, col %d" line col))
      bounds)))

(defun llm-clj-edit--check-parens ()
  (condition-case nil
      (save-excursion (goto-char (point-min)) (check-parens) t)
    (error (error "check-parens failed"))))

(defun llm-clj-edit--buffer-diff (file)
  (let ((old (llm-clj-edit--read-file file))
        (new (buffer-string)))
    (if (string= old new) ""
      (with-temp-buffer
        (insert old)
        (let ((old-buf (current-buffer)))
          (with-temp-buffer
            (insert new)
            (diff old-buf (current-buffer) nil 'noasync)
            (buffer-string)))))))

(defun llm-clj-edit--op-snapshot (_op)
  (save-excursion
    (goto-char (point-min))
    (let ((forms nil) (index 0))
      (while (not (eobp))
        (skip-chars-forward " \t\n")
        (when (looking-at "(")
          (let ((beg (point)) (line (line-number-at-pos)) (col (1+ (current-column))))
            (condition-case nil
                (progn
                  (forward-sexp)
                  (let* ((end (point))
                         (text (buffer-substring-no-properties beg end))
                         (head (condition-case nil (car (read text)) (error nil)))
                         (name (when (and head (symbolp head)) (symbol-name head))))
                    (when (member name '("ns" "def" "defn" "defn-" "defmacro" "defmulti"
                                         "defmethod" "defprotocol" "defrecord" "deftype"))
                      (setq index (1+ index))
                      (push
                       (list
                        (cons "index" index)
                        (cons "kind" (if (string= name "ns") "ns" "def"))
                        (cons "head" name)
                        (cons "range" (vector (vector line col)
                                              (vector (line-number-at-pos) (current-column))))
                        (cons "preview" (if (> (length text) 120)
                                            (concat (substring text 0 117) "...")
                                          text))
                        (cons "sha256" (llm-clj-edit--sha256 text)))
                       forms))))
              (error (forward-char 1)))))
        (forward-line 1))
      (list (cons "ok" t)
            (cons "forms" (nreverse forms))
            (cons "file" (buffer-file-name))
            (cons "line_count" (count-lines (point-min) (point-max)))))))

(defun llm-clj-edit--op-get-form (op)
  (let* ((target (cdr (assoc 'target op)))
         (bounds (llm-clj-edit--form-at-target target))
         (text (buffer-substring-no-properties (car bounds) (cdr bounds))))
    (list (cons "ok" t)
          (cons "text" text)
          (cons "range" (vector (vector (line-number-at-pos (car bounds))
                                        (save-excursion (goto-char (car bounds)) (1+ (current-column))))
                                (vector (line-number-at-pos (cdr bounds))
                                        (save-excursion (goto-char (cdr bounds)) (current-column)))))
          (cons "sha256" (llm-clj-edit--sha256 text)))))

(defun llm-clj-edit--op-replace-form (op)
  (let* ((target (cdr (assoc 'target op)))
         (new-form (cdr (assoc 'new_form op)))
         (sha256-expected (cdr (assoc 'sha256 target)))
         (bounds (llm-clj-edit--form-at-target target))
         (old-text (buffer-substring-no-properties (car bounds) (cdr bounds))))
    (when (and sha256-expected
               (not (string= sha256-expected (llm-clj-edit--sha256 old-text))))
      (error "Target hash mismatch"))
    (delete-region (car bounds) (cdr bounds))
    (goto-char (car bounds))
    (insert new-form)
    (let ((new-end (point)))
      (indent-region (car bounds) new-end)
      (list (cons "ok" t)
            (cons "changed" t)
            (cons "old_sha256" (llm-clj-edit--sha256 old-text))
            (cons "new_sha256" (llm-clj-edit--sha256 new-form))
            (cons "range" (vector (vector (line-number-at-pos (car bounds))
                                          (save-excursion (goto-char (car bounds)) (1+ (current-column))))
                                  (vector (line-number-at-pos new-end)
                                          (save-excursion (goto-char new-end) (current-column)))))))))

(defun llm-clj-edit--op-insert-form-after (op)
  (let* ((target (cdr (assoc 'target op)))
         (new-form (cdr (assoc 'new_form op)))
         (bounds (llm-clj-edit--form-at-target target)))
    (goto-char (cdr bounds))
    (insert "\n" new-form)
    (let ((end (point)))
      (indent-region (car bounds) end)
      (list (cons "ok" t) (cons "changed" t) (cons "inserted_at" (line-number-at-pos (cdr bounds)))))))

(defun llm-clj-edit--op-insert-form-before (op)
  (let* ((target (cdr (assoc 'target op)))
         (new-form (cdr (assoc 'new_form op)))
         (bounds (llm-clj-edit--form-at-target target)))
    (goto-char (car bounds))
    (insert new-form "\n")
    (let ((end (point)))
      (indent-region (car bounds) end)
      (list (cons "ok" t) (cons "changed" t) (cons "inserted_at" (line-number-at-pos (car bounds)))))))

(defun llm-clj-edit--op-remove-form (op)
  (let* ((target (cdr (assoc 'target op)))
         (bounds (llm-clj-edit--form-at-target target))
         (old-text (buffer-substring-no-properties (car bounds) (cdr bounds))))
    (delete-region (car bounds) (cdr bounds))
    (when (and (looking-at "\n") (not (bolp))) (delete-char 1))
    (list (cons "ok" t) (cons "changed" t) (cons "removed_text" old-text)
          (cons "old_sha256" (llm-clj-edit--sha256 old-text)))))

(defun llm-clj-edit--op-validate (_op)
  (llm-clj-edit--check-parens)
  (list (cons "ok" t) (cons "check_parens" "ok") (cons "file" (buffer-file-name))))

(defun llm-clj-edit--dispatch (op)
  (let* ((file (cdr (assoc 'file op)))
         (operation (cdr (assoc 'op op)))
         (save-flag (cdr (assoc 'save op)))
         (dry-run (eq save-flag :json-false))
         result)
    (unless operation (error "Missing 'op' field"))
    (when file
      (unless (llm-clj-edit--inside-project-p file)
        (error "File is outside allowed project root: %s" file))
      (find-file file)
      (llm-clj-edit--ensure-clojure-mode file))
    (setq result
          (condition-case err
              (progn
                (atomic-change-group
                  (setq result
                        (pcase operation
                          ("snapshot" (llm-clj-edit--op-snapshot op))
                          ("get-form" (llm-clj-edit--op-get-form op))
                          ("replace-form" (llm-clj-edit--op-replace-form op))
                          ("insert-form-after" (llm-clj-edit--op-insert-form-after op))
                          ("insert-form-before" (llm-clj-edit--op-insert-form-before op))
                          ("remove-form" (llm-clj-edit--op-remove-form op))
                          ("validate" (llm-clj-edit--op-validate op))
                          (_ (error "Unsupported op: %s" operation))))
                  (when (member operation '("replace-form" "insert-form-after"
                                            "insert-form-before" "remove-form"))
                    (llm-clj-edit--check-parens))
                  (when dry-run
                    (setq result (append result '(("dry_run" . t)))))
                  (when (and save-flag (not dry-run))
                    (basic-save-buffer)
                    (setq result (append result '(("saved" . t)))))
                  result))
            (error
             (list (cons "ok" :json-false)
                   (cons "error" (error-message-string err))
                   (cons "rolled_back" t)))))
    (when (and file (buffer-modified-p) (not dry-run))
      (let ((diff-text (llm-clj-edit--buffer-diff file)))
        (unless (string= diff-text "")
          (setq result (append result (list (cons "diff" diff-text)))))))
    result))

(defun llm-clj-edit-apply-file (input-json output-json)
  "Apply one safe Clojure structural edit."
  (let* ((raw (llm-clj-edit--read-file input-json))
         (op (json-parse-string raw
                                :object-type 'alist
                                :array-type 'list
                                :null-object nil
                                :false-object :json-false))
         (result
          (condition-case err
              (llm-clj-edit--dispatch op)
            (error
             (list (cons "ok" :json-false)
                   (cons "error" (error-message-string err)))))))
    (llm-clj-edit--write-file output-json (json-encode result))
    output-json))

(provide 'llm-clj-edit)
;;; llm-clj-edit.el ends here
