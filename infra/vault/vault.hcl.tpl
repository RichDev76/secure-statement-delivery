# ./vault/vault.hcl.tpl  (File backend - persistent single-node)
listener "tcp" {
  address     = "0.0.0.0:8200"
  tls_disable = 1
}

storage "file" {
  path = "/vault/data"
}

ui = true

api_addr     = "http://0.0.0.0:8200"
cluster_addr = "http://0.0.0.0:8201"
