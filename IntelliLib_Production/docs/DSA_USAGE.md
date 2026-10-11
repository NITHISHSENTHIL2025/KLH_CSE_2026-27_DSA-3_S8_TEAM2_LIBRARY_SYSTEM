# Where DSA is Used — Easy Explanation

The normal IntelliLib UI deliberately does not show algorithm names. They are implementation details underneath real library features.

## Hash table — exact lookup

**Used for:** ISBN lookup and token indexing inside the search engine.

**Why:** An ISBN is an exact identifier. A hash-based index avoids checking every resource one by one.

## Trie — live autocomplete

**Used for:** Title and author suggestions while a student types.

**Why:** Prefix search is the natural use of a trie. Entering a few starting letters can immediately retrieve matching title/author prefixes.

## AVL tree — sorted browsing

**Used for:** Alphabetical title indexing and prefix/range-style browsing.

**Why:** The tree remains balanced while resources are added, so ordered traversal stays efficient.

## Graph — related resources

**Used for:** Related Books.

**Why:** Books are connected through shared category, author, and keywords. The graph stores these relationships so nearby resources can be discovered quickly.

## KMP — text matching

**Used for:** Verifying query occurrence in searchable book metadata.

**Why:** It avoids restarting the comparison from the beginning after each mismatch.

## Rabin–Karp — available in the search engine module

**Used for:** Rolling-hash string-matching support where hash-based text matching is useful.

**Why:** It gives another efficient pattern-search technique and demonstrates polynomial rolling hash.

## Edit distance — typo recovery

**Used for:** Recovering useful matches when an exact token is not found.

**Why:** It measures how many edits separate the typed word and a known word, allowing small spelling mistakes to still find relevant resources.

## Important design point

PostgreSQL is used for durable storage and transactions. It does not replace the in-memory DSA search engine. On startup/import, IntelliLib rebuilds its search structures from the real database.
