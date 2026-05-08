# Generic dialect

Pass-through wrapper. Use as a fallback for model families without a tuned dialect (Llama, Mistral, custom). The agent body is rendered as-is.

To create a tuned dialect for a new family:

```
cp -r dialects/generic dialects/<new-family>
# edit dialects/<new-family>/dialect.yaml: id, applies_to_families
# edit dialects/<new-family>/wrappers/*.md to taste
```
