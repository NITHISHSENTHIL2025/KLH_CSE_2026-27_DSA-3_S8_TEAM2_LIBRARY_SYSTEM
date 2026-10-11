package com.intellilib.client.ui;

import com.intellilib.client.ClientModels.Book;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

public final class BookCard extends VBox {
    public BookCard(Book b, Consumer<Book> open){
        setSpacing(8);setPadding(new Insets(18));getStyleClass().add("book-card");setPrefWidth(255);setMinHeight(175);
        Label type=new Label((b.resourceType()==null?"RESOURCE":b.resourceType()).replace('_',' '));type.getStyleClass().add("eyebrow");
        Label t=new Label(b.title());t.setWrapText(true);t.getStyleClass().add("book-title");
        Label a=new Label(b.author());a.getStyleClass().add("muted");a.setWrapText(true);
        String meta=(b.category()==null?"General":b.category())+(b.publicationYear()>0?"  •  "+b.publicationYear():"");
        Label m=new Label(meta);m.getStyleClass().addAll("muted","small");
        Label avail=new Label(b.availableCopies()>0?b.availableCopies()+" available":"Unavailable");avail.getStyleClass().add(b.availableCopies()>0?"pill-success":"pill-muted");
        getChildren().addAll(type,t,a,m,avail);setOnMouseClicked(e->open.accept(b));UiKit.hoverLift(this);
    }
}
