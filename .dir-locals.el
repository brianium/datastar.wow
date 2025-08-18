((clojure-mode
  (cider-clojure-cli-aliases . ":dev")
  (eval . (setq clj/project-cider-refresh "(clj-reload.core/reload)"))))
