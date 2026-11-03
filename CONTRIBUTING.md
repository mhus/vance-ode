# Contributing

Contributions are welcome. This repository uses the
[Developer Certificate of Origin](https://developercertificate.org/) (DCO)
instead of a CLA: you keep the copyright to your work and confirm that you
may submit it under the repository's licence.

## Sign off your commits

Add a `Signed-off-by` line to every commit:

```
git commit -s -m "Describe the change"
```

This appends `Signed-off-by: Your Name <you@example.com>` and certifies the
DCO. A pull request check rejects commits without it. To add it to commits
you already made:

```
git rebase --signoff origin/main
git push --force-with-lease
```
