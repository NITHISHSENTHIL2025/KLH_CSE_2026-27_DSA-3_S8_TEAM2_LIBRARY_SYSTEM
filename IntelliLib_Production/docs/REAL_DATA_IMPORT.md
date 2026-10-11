# Real Data Import

Production starts without generated library books. Import real records from the Admin → Library / Students screens.

## Students CSV

Required columns:

```text
student_code,name,card_code
```

Optional:

```text
email,department,study_year,active
```

`card_code` is the exact text decoded from the QR/barcode on that student's real ID card. IntelliLib hashes it with the server-side card pepper before storage.

Template: `data/templates/students_template.csv`

## Books CSV

Required:

```text
title,author
```

Recommended:

```text
isbn,category,keywords,resource_type,publisher,publication_year,description,language,rating
```

Optional `barcode` can be included if each row already represents one physical copy. Duplicate ISBN rows update metadata rather than inventing a new title.

Template: `data/templates/books_template.csv`

## Physical copies CSV

For a proper physical library, each copy has its own unique barcode/accession code:

```text
isbn,barcode
```

Example conceptually:

```text
9780000000001,LIB-ACC-000001
9780000000001,LIB-ACC-000002
```

Template: `data/templates/book_copies_template.csv`

The barcode should be whatever the camera can actually scan on that physical book. If your library has no per-copy barcodes, print and attach IntelliLib accession labels before using issue/return scanning.
