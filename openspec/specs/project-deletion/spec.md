# Project deletion

The project menu offers online deletion. A preview counts the full project tree including archived descendants and completed tasks. Empty projects need one confirmation; nonempty projects need a second permanent-deletion confirmation. Cancel sends no DELETE request.

Before deleting, fetch and compare the tree again. Changed contents reset confirmation. Incomplete pagination, changed credentials, or any unsent changes block deletion. The operation is serialized with sync and database writes. Server refusal retains local contents, and failed requests are never retried automatically. The server enforces administrator access and delete permission. Successful deletion removes the project tree and cached task history. Changes from other clients after the final comparison remain a server-side race.
