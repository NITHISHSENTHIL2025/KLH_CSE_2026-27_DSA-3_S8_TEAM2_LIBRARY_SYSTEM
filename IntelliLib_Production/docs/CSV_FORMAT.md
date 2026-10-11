# CSV Formats

Use the exact header-only templates in `data/templates/`.

## Students
`student_code,name,email,department,study_year,card_code,active`

Required: `student_code`, `name`, `card_code`.

## Books
`title,author,isbn,category,keywords,resource_type,publisher,publication_year,description,language,rating,barcode`

Required: `title`, `author`. `barcode` is optional and can register one physical copy on the same row.

## Physical copies
`isbn,barcode`

Use one row per physical copy. Barcode must be unique.
