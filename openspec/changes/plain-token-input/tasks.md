# Tasks

## 1. Token field

- [x] 1.1 Make the setup screen's token field a visible text field (no password transformation or password keyboard type; autocorrect and capitalisation off); verify the CI build compiles and the existing setup view-model tests still pass
- [ ] 1.2 Verify on device that a copied `tk_…` token can be pasted and edited on the Connect to Vikunja screen and that connecting still works

## Workflow follow-up

- Close issue #3 with the pull request. Its spec is already synced into `openspec/specs/`; after the on-device check archive it with `openspec archive plain-token-input --skip-specs`.
