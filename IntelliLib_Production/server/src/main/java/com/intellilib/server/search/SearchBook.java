package com.intellilib.server.search;
public record SearchBook(long id,String title,String author,String isbn,String category,String keywords,String resourceType,
                         String publisher,int year,String description,String language,double rating,int totalCopies,int availableCopies) {
    public String searchable(){ return (title+" "+author+" "+isbn+" "+category+" "+keywords+" "+description).toLowerCase(); }
}
