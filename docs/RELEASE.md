# Release of qDup

This document summarizes all the steps needed to release next version of qDup.

It is assumed that you have all the priviledges on Sonatype, GitHub push rights and set up GPG keys.

Start with building and releasing artefacts:

```
$ mvn release:prepare -Prelease
What is the release version for "qDup"? ... <- Use semantic version (X.Y.Z), default guessed by Maven works.
...
What is SCM release tag or label for "qDup"? <- Use tag 'release-X.Y.Z'. N.B. a tag opf `X.Y.Z` appears to perform a snapshot release
What is the new development version for "qDup"? ... <- Use semantic version (X.Y.Z) with -SNAPSHOT suffix
...
$ mvn release:perform -Prelease
```

This creates the tag and pushes everything in the Hyperfoil/qDup repo.
