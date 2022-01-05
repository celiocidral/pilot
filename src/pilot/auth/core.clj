(ns pilot.auth.core)

(defprotocol AuthProvider
  (get-access-token [auth-provider credentials]))
