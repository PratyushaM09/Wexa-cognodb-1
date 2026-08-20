# Dataset

Use a public graph dataset for final results. The assignment requires at least 100,000 relationships and asks for roughly 100,000 to 500,000 relationships so the graph fits the smallest free tier.

Expected CSV format:

```csv
source,target
1,2
2,3
```

Final dataset used:

- Source: SNAP wiki-Vote network dataset
- Download URL: `http://snap.stanford.edu/data/wiki-Vote.txt.gz`
- Reference page: `https://snap.stanford.edu/data/wiki-Vote.html`
- Final size: 7,115 nodes and 103,689 relationships
- Node label used by the harness: `Person`
- Relationship type used by the harness: `KNOWS`
- Indexed properties: `Person.id`, `Person.bucket`

The synthetic generator in this repo is only for smoke testing the code. Do not use synthetic data as the final assignment dataset.
