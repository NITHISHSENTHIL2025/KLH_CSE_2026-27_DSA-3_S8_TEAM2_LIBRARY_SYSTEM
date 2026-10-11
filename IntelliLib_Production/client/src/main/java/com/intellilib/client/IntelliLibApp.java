package com.intellilib.client;

import com.intellilib.client.ClientModels.*;
import com.intellilib.client.scanner.CameraScannerOverlay;
import com.intellilib.client.ui.BookCard;
import com.intellilib.client.ui.UiKit;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.nio.file.Files;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class IntelliLibApp extends Application {
    private final AppConfig config=new AppConfig();
    private ApiClient api;
    private StackPane root;
    private RealtimeClient realtime;
    private Stage stage;

    @Override public void start(Stage stage){
        this.stage=stage;api=new ApiClient(config);root=new StackPane();
        Scene scene=new Scene(root,1360,850);scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/ui/premium.css")).toExternalForm());
        stage.setTitle("IntelliLib");
        try{stage.getIcons().add(new javafx.scene.image.Image(Objects.requireNonNull(getClass().getResourceAsStream("/ui/app-icon.png"))));}catch(Exception ignored){}
        stage.setMinWidth(1080);stage.setMinHeight(700);stage.setScene(scene);stage.show();showLanding();
    }

    private void set(Node n){root.getChildren().setAll(n);UiKit.fadeIn(n);}
    private <T> void run(Supplier<T> task, Consumer<T> ok){
        Thread t=new Thread(()->{try{T result=task.get();Platform.runLater(()->ok.accept(result));}catch(Exception e){Platform.runLater(()->UiKit.showError(root,clean(e.getMessage())));}},"intellilib-task");t.setDaemon(true);t.start();
    }
    private static String clean(String s){if(s==null||s.isBlank())return "The operation could not be completed.";return s;}

    // ---------- Landing / authentication ----------
    private void showLanding(){
        closeRealtime();SessionContext.clear();
        BorderPane page=new BorderPane();page.setPadding(new Insets(26,32,28,32));
        HBox top=new HBox();top.setAlignment(Pos.CENTER_RIGHT);Button settings=UiKit.secondary("Server settings");top.getChildren().add(settings);page.setTop(top);

        VBox center=new VBox(18);center.setAlignment(Pos.CENTER);center.setMaxWidth(900);
        Label brand=UiKit.title("IntelliLib");brand.setStyle("-fx-font-size:40px;");
        Label subtitle=UiKit.muted("A fast, live library system built around real students, real books and real circulation.");subtitle.setMaxWidth(680);subtitle.setAlignment(Pos.CENTER);
        Label status=new Label("Connecting to live server…");

        Button student=landingCard("Student","Scan your college ID card and enter the library.");
        Button admin=landingCard("Administrator","Manage requests, books, students, issue and return.");
        HBox roles=new HBox(16,student,admin);roles.setAlignment(Pos.CENTER);
        center.getChildren().addAll(brand,subtitle,roles,status);page.setCenter(center);set(page);

        run(api::health,online->{status.setText(online?"● Connected to IntelliLib":"● Server unavailable — check Server settings");status.getStyleClass().add(online?"status-online":"status-offline");});
        student.setOnAction(e->CameraScannerOverlay.open(root,"Scan your college ID","Hold the QR or barcode on your registered ID card inside the frame.",token->run(()->api.cardLogin(token),r->{SessionContext.set(r);showStudent();})));
        admin.setOnAction(e->showAdminLogin());settings.setOnAction(e->showServerSettings());
    }

    private Button landingCard(String title,String sub){Button b=new Button(title+"\n"+sub);b.setPrefSize(340,122);b.setWrapText(true);b.getStyleClass().add("book-card");b.setStyle("-fx-font-size:16px;-fx-font-weight:bold;-fx-alignment:center-left;-fx-padding:22;");UiKit.hoverLift(b);return b;}

    private void showServerSettings(){
        TextField url=new TextField(config.server());url.setPromptText("https://your-service.onrender.com");
        Label info=UiKit.muted("Use your Render backend URL here. The setting is stored only on this PC.");
        Button test=UiKit.secondary("Test connection"),save=UiKit.primary("Save"),cancel=UiKit.secondary("Cancel");
        HBox buttons=new HBox(9,cancel,test,save);buttons.setAlignment(Pos.CENTER_RIGHT);
        VBox body=new VBox(12,UiKit.h2("Server connection"),info,url,buttons);UiKit.OverlayHandle h=UiKit.sheet(root,body,560);
        cancel.setOnAction(e->h.close());
        test.setOnAction(e->run(()->testServerUrl(url.getText()),ok->UiKit.toast(root,ok?"Connection successful":"Server did not respond")));
        save.setOnAction(e->{if(url.getText().isBlank()){UiKit.showError(root,"Enter the IntelliLib server URL.");return;}config.saveServer(url.getText());api=new ApiClient(config);h.close();UiKit.toast(root,"Server saved");showLanding();});
    }

    private boolean testServerUrl(String raw){
        try{String base=raw==null?"":raw.trim().replaceAll("/$","");if(base.isBlank())return false;var c=java.net.http.HttpClient.newHttpClient();var req=java.net.http.HttpRequest.newBuilder(java.net.URI.create(base+"/api/health")).timeout(java.time.Duration.ofSeconds(70)).GET().build();return c.send(req,java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode()==200;}catch(Exception e){return false;}
    }

    private void showAdminLogin(){
        VBox wrap=new VBox(18);wrap.setAlignment(Pos.CENTER);wrap.setPadding(new Insets(40));
        Label title=UiKit.title("Administrator");Label note=UiKit.muted("Secure access to the live library system.");
        TextField email=new TextField();email.setPromptText("Admin email");PasswordField pass=new PasswordField();pass.setPromptText("Password");
        Button login=UiKit.primary("Sign in"),back=UiKit.secondary("Back");
        HBox buttons=new HBox(8,back,login);buttons.setAlignment(Pos.CENTER_RIGHT);
        VBox card=UiKit.card(title,note,email,pass,buttons);card.setMaxWidth(470);wrap.getChildren().add(card);set(wrap);
        back.setOnAction(e->showLanding());pass.setOnAction(e->login.fire());
        login.setOnAction(e->{if(email.getText().isBlank()||pass.getText().isBlank()){UiKit.showError(root,"Enter your administrator email and password.");return;}run(()->api.adminLogin(email.getText().trim(),pass.getText()),r->{SessionContext.set(r);showAdmin("Dashboard");});});
    }

    // ---------- Shared shell ----------
    private BorderPane shell(String[] items, Consumer<String> nav){
        BorderPane p=new BorderPane();VBox side=new VBox(7);side.getStyleClass().add("sidebar");side.setPrefWidth(218);
        Label brand=new Label("IntelliLib");brand.getStyleClass().add("sidebar-title");side.getChildren().add(brand);
        if(SessionContext.user()!=null){Label who=UiKit.muted(SessionContext.user().name());who.setPadding(new Insets(0,6,14,6));side.getChildren().add(who);}
        ToggleGroup group=new ToggleGroup();boolean first=true;
        for(String item:items){ToggleButton b=new ToggleButton(item);b.setToggleGroup(group);b.setMaxWidth(Double.MAX_VALUE);b.getStyleClass().add("nav-button");b.setOnAction(e->nav.accept(item));side.getChildren().add(b);if(first){b.setSelected(true);first=false;}}
        Region spacer=new Region();VBox.setVgrow(spacer,Priority.ALWAYS);
        Button logout=new Button("Sign out");logout.setMaxWidth(Double.MAX_VALUE);logout.getStyleClass().add("nav-button");logout.setOnAction(e->{run(()->{try{api.logout();}catch(Exception ignored){}return true;},ok->showLanding());});side.getChildren().addAll(spacer,logout);p.setLeft(side);return p;
    }
    private ScrollPane scroll(Node n){ScrollPane s=new ScrollPane(n);s.setFitToWidth(true);s.setPannable(true);return s;}
    private VBox page(){VBox p=new VBox(20);p.getStyleClass().add("content");return p;}
    private void closeRealtime(){if(realtime!=null){realtime.close();realtime=null;}}

    // ---------- Student ----------
    private void showStudent(){
        SessionUser u=SessionContext.user();BorderPane[] ref=new BorderPane[1];
        BorderPane sh=shell(new String[]{"Home","Search","My Requests","My Books","Notifications"},item->showStudentPage(ref[0],item));ref[0]=sh;set(sh);
        realtime=new RealtimeClient();realtime.connect(config,u.id(),m->UiKit.toast(root,String.valueOf(m.getOrDefault("message","Library update"))),null);
        showStudentPage(sh,"Home");
    }
    private void showStudentPage(BorderPane sh,String page){switch(page){case"Home"->studentHome(sh);case"Search"->studentSearch(sh,"");case"My Requests"->studentRequests(sh);case"My Books"->studentLoans(sh);case"Notifications"->studentNotifications(sh);}}

    private void studentHome(BorderPane sh){
        VBox p=page();Label greet=UiKit.title("Good to see you, "+SessionContext.user().name());Label sub=UiKit.muted("Find a book, request it, and track everything from here.");
        TextField search=new TextField();search.setPromptText("Search by title, author, ISBN, category or keyword…");search.getStyleClass().add("search-field");
        FlowPane popular=new FlowPane(14,14),recommended=new FlowPane(14,14);
        p.getChildren().addAll(greet,sub,search,UiKit.h2("Popular in the library"),popular,UiKit.h2("Recommended for you"),recommended);sh.setCenter(scroll(p));
        search.setOnAction(e->studentSearch(sh,search.getText()));
        run(api::popular,b->fillBooks(popular,b,"No borrowing history yet","Popular books will appear after real loans begin."));
        run(api::recommended,b->fillBooks(recommended,b,"Nothing to recommend yet","Recommendations appear from your own library activity."));
    }

    private void studentSearch(BorderPane sh,String initial){
        VBox p=page();p.getChildren().add(UiKit.title("Search the library"));TextField q=new TextField(initial);q.setPromptText("Title, author, ISBN, category or keyword…");q.getStyleClass().add("search-field");
        ListView<String> suggests=new ListView<>();suggests.setMaxHeight(160);suggests.setVisible(false);suggests.setManaged(false);Label summary=UiKit.muted("");FlowPane results=new FlowPane(14,14);
        p.getChildren().addAll(q,suggests,summary,results);sh.setCenter(scroll(p));PauseTransition pause=new PauseTransition(Duration.millis(180));
        q.textProperty().addListener((o,a,b)->{pause.stop();if(b==null||b.trim().length()<2){suggests.setVisible(false);suggests.setManaged(false);return;}pause.setOnFinished(e->run(()->api.suggestions(b),list->{suggests.getItems().setAll(list.stream().map(Suggestion::text).toList());boolean show=!list.isEmpty();suggests.setVisible(show);suggests.setManaged(show);}));pause.playFromStart();});
        suggests.setOnMouseClicked(e->{String x=suggests.getSelectionModel().getSelectedItem();if(x!=null){q.setText(x);doSearch(q.getText(),results,summary);suggests.setVisible(false);suggests.setManaged(false);}});
        q.setOnAction(e->doSearch(q.getText(),results,summary));if(!initial.isBlank())doSearch(initial,results,summary);
    }

    private void doSearch(String query,FlowPane results,Label summary){
        if(query==null||query.isBlank())return;results.getChildren().clear();summary.setText("Searching…");
        run(()->api.search(query.trim()),r->{summary.setText(r.total()+" result"+(r.total()==1?"":"s")+" • "+r.durationMs()+" ms");fillBooks(results,r.books(),"No matching books","Try another title, author, ISBN, category or keyword.");});
    }

    private void fillBooks(FlowPane pane,List<Book> books,String emptyTitle,String emptyMessage){pane.getChildren().clear();if(books==null||books.isEmpty()){pane.getChildren().add(UiKit.empty(emptyTitle,emptyMessage));return;}for(Book b:books)pane.getChildren().add(new BookCard(b,this::showStudentBook));}

    private void showStudentBook(Book b){
        run(()->{api.viewed(b.id());return api.related(b.id());},related->showBookSheet(b,related,true));
    }

    private void showBookSheet(Book b,List<Book> related,boolean student){
        VBox body=new VBox(15);body.setMaxWidth(860);
        Label title=UiKit.title(b.title());title.setWrapText(true);
        Label author=UiKit.muted(b.author());author.setStyle("-fx-font-size:15px;");
        String availability=b.availableCopies()>0?b.availableCopies()+" of "+b.totalCopies()+" copies available":"Currently unavailable";
        Label avail=new Label(availability);avail.getStyleClass().add(b.availableCopies()>0?"pill-success":"pill-muted");

        GridPane details=new GridPane();details.setHgap(22);details.setVgap(8);int row=0;
        row=detail(details,row,"Category",b.category());
        row=detail(details,row,"ISBN",b.isbn());
        row=detail(details,row,"Publisher",b.publisher());
        row=detail(details,row,"Year",b.publicationYear()>0?String.valueOf(b.publicationYear()):"");
        row=detail(details,row,"Language",b.language());
        row=detail(details,row,"Type",b.resourceType());

        Label description=UiKit.muted(b.description()==null||b.description().isBlank()?"No description provided.":b.description());
        description.setMaxWidth(820);

        UiKit.OverlayHandle[] currentSheet=new UiKit.OverlayHandle[1];
        Consumer<Book> openRelated=x->{
            if(currentSheet[0]!=null)currentSheet[0].close();
            if(student)showStudentBook(x);
            else run(()->api.related(x.id()),rel->showBookSheet(x,rel,false));
        };

        HBox relatedRow=new HBox(10);
        if(related==null||related.isEmpty()){
            relatedRow.getChildren().add(UiKit.muted("No related books yet."));
        }else{
            related.stream().limit(8).forEach(x->relatedRow.getChildren().add(new BookCard(x,openRelated)));
        }
        ScrollPane relatedScroll=new ScrollPane(relatedRow);
        relatedScroll.setFitToHeight(true);
        relatedScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        relatedScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        relatedScroll.setPannable(true);
        relatedScroll.setPrefViewportHeight(205);
        relatedScroll.getStyleClass().add("related-scroll");

        Button close=UiKit.secondary("Close");
        HBox actions=new HBox(9);actions.setAlignment(Pos.CENTER_RIGHT);actions.getStyleClass().add("action-strip");actions.getChildren().add(close);
        if(student){
            Button request=UiKit.primary(b.availableCopies()>0?"Request book":"Unavailable");
            request.setDisable(b.availableCopies()<1);
            actions.getChildren().add(request);
            request.setOnAction(e->UiKit.confirm(root,"Request this book?",b.title()+"\n\nThe librarian will review the request before collection.","Request",()->run(()->{api.requestBorrow(b.id());return true;},ok->{UiKit.toast(root,"Request sent");request.setDisable(true);request.setText("Requested");})));
        }else{
            Button addCopy=UiKit.primary("Scan & add physical copy");actions.getChildren().add(addCopy);
            addCopy.setOnAction(e->CameraScannerOverlay.open(root,"Scan the book copy","Scan the barcode or QR attached to this physical copy.",barcode->run(()->api.addCopy(b.id(),barcode),x->{UiKit.toast(root,"Physical copy added");addCopy.setText("Copy added");})));
        }

        body.getChildren().addAll(
                UiKit.eyebrow(b.resourceType()==null?"RESOURCE":b.resourceType()),
                title,author,avail,new Separator(),description,details,
                UiKit.h2("Related books"),relatedScroll,actions
        );
        UiKit.OverlayHandle h=UiKit.sheet(root,body,920);
        currentSheet[0]=h;
        close.setOnAction(e->h.close());
    }
    private int detail(GridPane g,int row,String k,String v){if(v==null||v.isBlank())return row;Label key=UiKit.muted(k);Label value=new Label(v);value.setWrapText(true);g.add(key,0,row);g.add(value,1,row);return row+1;}

    private void studentRequests(BorderPane sh){
        VBox p=page();p.getChildren().add(UiKit.title("My Requests"));VBox list=new VBox(10);p.getChildren().add(list);sh.setCenter(scroll(p));
        run(api::myRequests,rows->{list.getChildren().clear();if(rows.isEmpty()){list.getChildren().add(UiKit.empty("No requests yet","Request a book from Search and it will appear here."));return;}
            for(var x:rows){
                Label s=new Label(prettyStatus(x.status()));s.getStyleClass().add(statusClass(x.status()));
                String when=x.requestedAt()==null?"":"Requested "+formatTime(x.requestedAt());
                String expiry=("APPROVED".equals(x.status())&&x.expiresAt()!=null)?" • collect before "+x.expiresAt().toLocalDate():"";
                list.getChildren().add(UiKit.card(UiKit.h3(x.bookTitle()),UiKit.muted(x.author()),UiKit.muted(when+expiry),s));
            }
        });
    }
    private void studentLoans(BorderPane sh){
        VBox p=page();p.getChildren().add(UiKit.title("My Books"));VBox list=new VBox(10);p.getChildren().add(list);sh.setCenter(scroll(p));
        run(api::myLoans,rows->{list.getChildren().clear();if(rows.isEmpty()){list.getChildren().add(UiKit.empty("No borrowed books","Books appear here after the librarian issues a physical copy to you."));return;}
            for(var x:rows){
                String timing=x.returnedAt()!=null?"Returned "+x.returnedAt().toLocalDate():(x.dueAt()==null?"":"Due "+x.dueAt().toLocalDate());
                Label s=new Label(prettyStatus(x.status()));s.getStyleClass().add(statusClass(x.status()));
                list.getChildren().add(UiKit.card(UiKit.h3(x.title()),UiKit.muted(x.author()),UiKit.muted("Copy "+x.barcode()+(timing.isBlank()?"":" • "+timing)),s));
            }
        });
    }
    private void studentNotifications(BorderPane sh){VBox p=page();p.getChildren().add(UiKit.title("Notifications"));VBox list=new VBox(10);p.getChildren().add(list);sh.setCenter(scroll(p));run(api::notifications,rows->{list.getChildren().clear();if(rows.isEmpty()){list.getChildren().add(UiKit.empty("You're all caught up","Approvals, issue confirmations and due reminders appear here."));return;}for(var x:rows)list.getChildren().add(UiKit.card(UiKit.h3(x.title()),UiKit.muted(x.message()),UiKit.muted(formatTime(x.createdAt()))));});}

    // ---------- Administrator ----------
    private void showAdmin(String initial){
        BorderPane[] ref=new BorderPane[1];String[] pages={"Dashboard","Requests","Library","Students","Issue / Return","Loans","Activity"};
        BorderPane sh=shell(pages,item->showAdminPage(ref[0],item));ref[0]=sh;set(sh);
        realtime=new RealtimeClient();realtime.connect(config,SessionContext.user().id(),m->UiKit.toast(root,String.valueOf(m.getOrDefault("message","Update"))),m->UiKit.toast(root,"New borrow request"));
        showAdminPage(sh,initial);
    }
    private void showAdminPage(BorderPane sh,String page){switch(page){case"Dashboard"->adminDashboard(sh);case"Requests"->adminRequests(sh);case"Library"->adminLibrary(sh);case"Students"->adminStudents(sh);case"Issue / Return"->adminCirculation(sh);case"Loans"->adminLoans(sh);case"Activity"->adminActivity(sh);}}

    private void adminDashboard(BorderPane sh){
        VBox p=page();p.getChildren().addAll(UiKit.title("Library overview"),UiKit.muted("Every number below comes directly from the live database."));FlowPane cards=new FlowPane(14,14);VBox note=new VBox(12);p.getChildren().addAll(cards,note);sh.setCenter(scroll(p));
        run(api::dashboard,d->{
            cards.getChildren().setAll(UiKit.statCard("Books",d.books()),UiKit.statCard("Physical copies",d.copies()),UiKit.statCard("Available",d.availableCopies()),UiKit.statCard("Students",d.students()),UiKit.statCard("Issued",d.activeLoans()),UiKit.statCard("Pending requests",d.pendingRequests()),UiKit.statCard("Overdue",d.overdueLoans()));
            note.getChildren().clear();
            if(d.students()==0){Button register=UiKit.primary("Register first student");register.setOnAction(e->adminStudents(sh));note.getChildren().add(UiKit.card(UiKit.h2("Enroll real student ID cards"),UiKit.muted("No students are enrolled yet. Scan a real college ID once from Students → Register one student before student login can work."),register));}
            if(d.books()==0){Button go=UiKit.secondary("Import real library data");go.setOnAction(e->adminLibrary(sh));note.getChildren().add(UiKit.card(UiKit.h2("Import the real library catalogue"),UiKit.muted("Import your real books CSV and physical-copy barcodes. IntelliLib does not preload generated production data."),go));}
        });
    }

    private void adminRequests(BorderPane sh){
    VBox p = page();

    HBox title = new HBox(12, UiKit.title("Borrow requests"));
    title.setAlignment(Pos.CENTER_LEFT);

    Button refresh = UiKit.secondary("Refresh");
    title.getChildren().add(refresh);

    VBox list = new VBox(10);

    p.getChildren().addAll(title, list);
    sh.setCenter(scroll(p));

    Runnable[] load = new Runnable[1];

    load[0] = () -> run(api::pending, rows -> {
        list.getChildren().clear();

        if(rows.isEmpty()){
            list.getChildren().add(
                UiKit.empty(
                    "No pending requests",
                    "New student requests appear here live."
                )
            );
            return;
        }

        for(var x : rows){
            Label available = new Label(x.availableCopies() + " available");
            available.getStyleClass().add(
                x.availableCopies() > 0 ? "pill-success" : "pill-muted"
            );

            Button approve = UiKit.primary(x.availableCopies()>0?"Approve":"No copy available");
            approve.setDisable(x.availableCopies()<=0);
            Button reject = UiKit.secondary("Reject");

            HBox actions = new HBox(8, reject, approve);
            actions.setAlignment(Pos.CENTER_RIGHT);

            VBox card = UiKit.card(
                UiKit.h3(x.userName() + "  →  " + x.bookTitle()),
                UiKit.muted(x.studentCode() + " • " + x.author()),
                UiKit.muted(x.requestedAt()==null?"":"Requested "+formatTime(x.requestedAt())),
                available,
                actions
            );

            list.getChildren().add(card);

            approve.setOnAction(e ->
                run(
                    () -> {
                        api.decide(x.id(), true, "");
                        return true;
                    },
                    ok -> {
                        UiKit.toast(root, "Request approved");
                        load[0].run();
                    }
                )
            );

            reject.setOnAction(e ->
                rejectRequest(x.id(), load[0])
            );
        }
    });

    load[0].run();

    refresh.setOnAction(e -> load[0].run());
}
    private void rejectRequest(long id,Runnable reload){TextArea reason=new TextArea();reason.setPromptText("Reason (optional)");reason.setPrefRowCount(3);Button cancel=UiKit.secondary("Cancel"),reject=UiKit.danger("Reject request");HBox b=new HBox(8,cancel,reject);b.setAlignment(Pos.CENTER_RIGHT);VBox body=new VBox(12,UiKit.h2("Reject request"),UiKit.muted("The student will receive your message."),reason,b);UiKit.OverlayHandle h=UiKit.sheet(root,body,520);cancel.setOnAction(e->h.close());reject.setOnAction(e->{h.close();run(()->{api.decide(id,false,reason.getText());return true;},ok->{UiKit.toast(root,"Request rejected");reload.run();});});}

    private void adminLibrary(BorderPane sh){
        VBox p=page();HBox tools=new HBox(8);Button importBooks=UiKit.primary("Import books CSV"),importCopies=UiKit.secondary("Import copy barcodes"),export=UiKit.secondary("Export books"),add=UiKit.secondary("Add book");tools.getChildren().addAll(importBooks,importCopies,export,add);
        TextField q=new TextField();q.setPromptText("Search the live library…");q.getStyleClass().add("search-field");Label summary=UiKit.muted("");FlowPane results=new FlowPane(14,14);p.getChildren().addAll(UiKit.title("Library"),UiKit.muted("Real metadata and exact physical copies. Import data or add a book manually."),tools,q,summary,results);sh.setCenter(scroll(p));

        Runnable loadCatalogue = () -> {
            summary.setText("Loading catalogue…");
            results.getChildren().clear();
            run(api::dashboard,d->{
                if(d.books()==0){
                    summary.setText("");
                    results.getChildren().setAll(UiKit.empty("No books imported","Use ‘Import books CSV’ to load the real library catalogue."));
                    return;
                }
                run(()->api.browse("",80),books->{
                    summary.setText(String.format("%,d books • %,d physical copies • showing first %,d",d.books(),d.copies(),books.size()));
                    results.getChildren().clear();
                    if(books.isEmpty()) results.getChildren().add(UiKit.empty("Catalogue loaded","Use search to find a book."));
                    else for(Book b:books) results.getChildren().add(new BookCard(b,x->run(()->api.related(x.id()),rel->showBookSheet(x,rel,false))));
                });
            });
        };

        q.setOnAction(e->{if(q.getText()==null||q.getText().isBlank())loadCatalogue.run();else doAdminSearch(q.getText(),results,summary);});
        Consumer<ImportResult> imported = r -> {
            showImportResult(r);
            loadCatalogue.run();
        };
        importBooks.setOnAction(e->chooseCsv("Import books CSV",path->run(()->api.importBooks(path),imported)));
        importCopies.setOnAction(e->chooseCsv("Import physical copies CSV",path->run(()->api.importCopies(path),imported)));
        export.setOnAction(e->exportBooks());add.setOnAction(e->addBookOverlay());
        loadCatalogue.run();
    }
    private void doAdminSearch(String query,FlowPane results,Label summary){if(query==null||query.isBlank())return;run(()->api.search(query),r->{summary.setText(r.total()+" result"+(r.total()==1?"":"s"));results.getChildren().clear();if(r.books().isEmpty())results.getChildren().add(UiKit.empty("No results","No live library record matched your search."));else for(Book b:r.books())results.getChildren().add(new BookCard(b,x->run(()->api.related(x.id()),rel->showBookSheet(x,rel,false))));});}

    private void addBookOverlay(){
        TextField title=new TextField(),author=new TextField(),isbn=new TextField(),category=new TextField(),keywords=new TextField(),publisher=new TextField(),year=new TextField();TextArea description=new TextArea();description.setPrefRowCount(3);
        title.setPromptText("Title *");author.setPromptText("Author *");isbn.setPromptText("ISBN");category.setPromptText("Category");keywords.setPromptText("Keywords");publisher.setPromptText("Publisher");year.setPromptText("Publication year");description.setPromptText("Description");
        Button cancel=UiKit.secondary("Cancel"),save=UiKit.primary("Add book");HBox buttons=new HBox(8,cancel,save);buttons.setAlignment(Pos.CENTER_RIGHT);VBox body=new VBox(9,UiKit.h2("Add book"),title,author,isbn,category,keywords,publisher,year,description,buttons);UiKit.OverlayHandle h=UiKit.sheet(root,body,600);cancel.setOnAction(e->h.close());
        save.setOnAction(e->{if(title.getText().isBlank()||author.getText().isBlank()){UiKit.showError(root,"Title and author are required.");return;}Map<String,Object> m=new LinkedHashMap<>();m.put("title",title.getText());m.put("author",author.getText());m.put("isbn",isbn.getText());m.put("category",category.getText());m.put("keywords",keywords.getText());m.put("resourceType","BOOK");m.put("publisher",publisher.getText());m.put("publicationYear",parseInt(year.getText()));m.put("description",description.getText());m.put("language","English");m.put("rating",0);m.put("copies",0);h.close();run(()->api.createBook(m),id->{UiKit.toast(root,"Book added. Scan physical copies from the book detail.");});});
    }

    private void adminStudents(BorderPane sh){
        VBox p=page();
        Button importCsv=UiKit.primary("Import students CSV"),register=UiKit.secondary("Register one student"),refresh=UiKit.secondary("Refresh"),toggle=UiKit.secondary("Disable selected");
        toggle.setDisable(true);
        HBox tools=new HBox(8,importCsv,register,refresh,toggle);tools.getStyleClass().add("table-toolbar");
        TextField filter=new TextField();filter.setPromptText("Search student ID, name or department…");filter.getStyleClass().add("search-field");
        TableView<UserView> table=new TableView<>();
        table.setPlaceholder(UiKit.muted("No students found."));
        TableColumn<UserView,String> code=column("Student ID",UserView::studentCode),name=column("Name",UserView::name),dept=column("Department",UserView::department),year=column("Year",UserView::studyYear),email=column("Email",UserView::email);
        TableColumn<UserView,String> state=column("Status",u->u.active()?"Active":"Disabled");
        table.getColumns().addAll(code,name,dept,year,email,state);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);table.setPrefHeight(620);VBox.setVgrow(table,Priority.ALWAYS);

        p.getChildren().addAll(UiKit.title("Students"),UiKit.muted("Enroll real ID cards, find student records and control access safely."),tools,filter,table);sh.setCenter(p);

        final List<UserView>[] all=new List[]{List.of()};
        Runnable applyFilter=()->{
            String needle=filter.getText()==null?"":filter.getText().trim().toLowerCase(Locale.ROOT);
            if(needle.isBlank())table.getItems().setAll(all[0]);
            else table.getItems().setAll(all[0].stream().filter(u->
                    (u.studentCode()!=null&&u.studentCode().toLowerCase(Locale.ROOT).contains(needle))||
                    (u.name()!=null&&u.name().toLowerCase(Locale.ROOT).contains(needle))||
                    (u.department()!=null&&u.department().toLowerCase(Locale.ROOT).contains(needle))||
                    (u.email()!=null&&u.email().toLowerCase(Locale.ROOT).contains(needle))
            ).toList());
        };
        Runnable load=()->run(api::users,rows->{all[0]=rows;applyFilter.run();});
        load.run();

        filter.textProperty().addListener((o,a,b)->applyFilter.run());
        table.getSelectionModel().selectedItemProperty().addListener((o,a,b)->{
            toggle.setDisable(b==null);
            if(b!=null)toggle.setText(b.active()?"Disable selected":"Enable selected");
        });
        toggle.setOnAction(e->{UserView u=table.getSelectionModel().getSelectedItem();if(u==null)return;boolean next=!u.active();run(()->{api.setUserActive(u.id(),next);return true;},ok->{UiKit.toast(root,next?"Student enabled":"Student disabled");load.run();});});
        refresh.setOnAction(e->load.run());
        importCsv.setOnAction(e->chooseCsv("Import students CSV",path->run(()->api.importStudents(path),r->{showImportResult(r);load.run();})));
        register.setOnAction(e->CameraScannerOverlay.open(root,"Scan the student's ID card","This scan enrolls the actual code printed on the student's college ID.",cardCode->registerStudentForm(cardCode,load)));
    }
    private <T> TableColumn<T,String> column(String title,java.util.function.Function<T,String> get){TableColumn<T,String> c=new TableColumn<>(title);c.setCellValueFactory(x->new javafx.beans.property.SimpleStringProperty(Optional.ofNullable(get.apply(x.getValue())).orElse("")));return c;}
    private void registerStudentForm(String cardCode,Runnable reload){TextField studentId=new TextField(),name=new TextField(),email=new TextField(),dept=new TextField(),year=new TextField();studentId.setPromptText("Student ID *");name.setPromptText("Name *");email.setPromptText("Email");dept.setPromptText("Department");year.setPromptText("Year");String normalized=cardCode==null?"":cardCode.strip();if(normalized.matches("[A-Za-z0-9_-]{6,64}"))studentId.setText(normalized);Button cancel=UiKit.secondary("Cancel"),create=UiKit.primary("Register student");HBox buttons=new HBox(8,cancel,create);buttons.setAlignment(Pos.CENTER_RIGHT);Label detected=UiKit.muted(normalized.isBlank()?"ID card scanned successfully.":"Card detected: "+normalized+". Verify the student details below.");VBox body=new VBox(9,UiKit.h2("Register student"),detected,studentId,name,email,dept,year,buttons);UiKit.OverlayHandle h=UiKit.sheet(root,body,560);cancel.setOnAction(e->h.close());Platform.runLater(name::requestFocus);create.setOnAction(e->{if(studentId.getText().isBlank()||name.getText().isBlank()){UiKit.showError(root,"Student ID and name are required.");return;}Map<String,Object> data=new LinkedHashMap<>();data.put("studentCode",studentId.getText().trim());data.put("name",name.getText().trim());data.put("email",email.getText().trim());data.put("department",dept.getText().trim());data.put("studyYear",year.getText().trim());data.put("cardCode",normalized);h.close();run(()->api.createUser(data),u->{UiKit.toast(root,"Student registered: "+u.name());reload.run();});});}

    private void adminCirculation(BorderPane sh){
        VBox p=page();p.getChildren().addAll(UiKit.title("Issue / Return"),UiKit.muted("Use the camera inside IntelliLib. Scan the student's ID, then the exact physical book copy."));
        Button issue=bigAction("Issue book","Approved request → student ID → physical copy"),ret=bigAction("Return book","Student ID → currently-issued physical copy");HBox row=new HBox(16,issue,ret);p.getChildren().add(row);sh.setCenter(p);issue.setOnAction(e->scanCirculation(true));ret.setOnAction(e->scanCirculation(false));
    }
    private Button bigAction(String title,String sub){Button b=new Button(title+"\n"+sub);b.setWrapText(true);b.setPrefSize(350,120);b.getStyleClass().add("book-card");b.setStyle("-fx-font-size:15px;-fx-font-weight:bold;-fx-alignment:center-left;-fx-padding:22;");UiKit.hoverLift(b);return b;}

    private void scanCirculation(boolean issue){
        CameraScannerOverlay.open(root,"Scan student ID",issue?"Scan the student collecting an approved book.":"Scan the student returning a book.",cardCode->run(()->api.inspectStudent(cardCode),student->{
            if(issue&&student.approvedRequests().isEmpty()){UiKit.showError(root,student.name()+" has no approved book ready for collection.");return;}
            String subtitle=issue?student.name()+" verified. Now scan the physical copy being issued.":student.name()+" verified. Now scan the physical copy being returned.";
            CameraScannerOverlay.open(root,"Scan physical book",subtitle,barcode->run(()->api.inspectBook(barcode),book->{
                if(issue&&student.approvedRequests().stream().noneMatch(r->r.bookId()==book.bookId())){UiKit.showError(root,"This scanned book is not one of "+student.name()+"'s approved requests.");return;}
                run(()->issue?api.issue(cardCode,barcode):api.returned(cardCode,barcode),result->showCirculationSuccess(result,issue));
            }));
        }));
    }
    private void showCirculationSuccess(CirculationResult r,boolean issue){String detail=r.studentName()+"\n"+r.bookTitle()+"\nCopy: "+r.bookBarcode()+(r.dueAt()!=null?"\nDue: "+r.dueAt().toLocalDate():"");UiKit.showMessage(root,issue?"Book issued":"Return complete",detail);}

    private void adminLoans(BorderPane sh){
        VBox p=page();HBox head=new HBox(12);head.setAlignment(Pos.CENTER_LEFT);Label title=UiKit.title("Loans");Button refresh=UiKit.secondary("Refresh");head.getChildren().addAll(title,refresh);
        TableView<AdminLoanView> table=new TableView<>();table.setPlaceholder(UiKit.muted("No loan history yet."));
        table.getColumns().addAll(column("Student",AdminLoanView::studentName),column("Student ID",AdminLoanView::studentCode),column("Book",AdminLoanView::bookTitle),column("Copy",AdminLoanView::barcode),column("Status",x->prettyStatus(x.status())),column("Issued",x->x.issuedAt()==null?"":x.issuedAt().toLocalDate().toString()),column("Due / Returned",x->x.returnedAt()!=null?"Returned "+x.returnedAt().toLocalDate():(x.dueAt()==null?"":"Due "+x.dueAt().toLocalDate())));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);table.setPrefHeight(650);VBox.setVgrow(table,Priority.ALWAYS);p.getChildren().addAll(head,table);sh.setCenter(p);
        Runnable load=()->run(api::adminLoans,rows->table.getItems().setAll(rows));load.run();refresh.setOnAction(e->load.run());
    }
    private void adminActivity(BorderPane sh){VBox p=page();p.getChildren().add(UiKit.title("Activity"));VBox list=new VBox(8);p.getChildren().add(list);sh.setCenter(scroll(p));run(api::activity,rows->{list.getChildren().clear();if(rows.isEmpty()){list.getChildren().add(UiKit.empty("No activity yet","Administrative actions and circulation events appear here."));return;}for(var x:rows){String actor=x.actorName()==null?"System":x.actorName();list.getChildren().add(UiKit.card(UiKit.h3(humanAction(x.action())),UiKit.muted(actor+" • "+formatTime(x.createdAt())),UiKit.muted(x.details()==null?"":x.details())));}});}

    // ---------- File/import helpers ----------
    private void chooseCsv(String title,Consumer<java.nio.file.Path> chosen){FileChooser fc=new FileChooser();fc.setTitle(title);fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV files","*.csv"));java.io.File f=fc.showOpenDialog(stage);if(f!=null)chosen.accept(f.toPath());}
    private void showImportResult(ImportResult r){String msg="Inserted: "+r.inserted()+"\nUpdated: "+r.updated()+"\nSkipped: "+r.skipped();if(r.warnings()!=null&&!r.warnings().isEmpty())msg+="\n\nWarnings:\n"+String.join("\n",r.warnings().stream().limit(8).toList());UiKit.showMessage(root,"Import complete",msg);}
    private void exportBooks(){FileChooser fc=new FileChooser();fc.setTitle("Export books");fc.setInitialFileName("intellilib_books.csv");java.io.File f=fc.showSaveDialog(stage);if(f!=null)run(()->{try{Files.write(f.toPath(),api.exportCsv());return true;}catch(Exception e){throw new IllegalStateException(e.getMessage(),e);}},ok->UiKit.toast(root,"Export saved"));}

    private static Integer parseInt(String s){try{return s==null||s.isBlank()?null:Integer.parseInt(s.trim());}catch(Exception e){return null;}}
    private static String formatTime(java.time.OffsetDateTime t){return t==null?"":t.format(DateTimeFormatter.ofPattern("dd MMM yyyy • h:mm a"));}
    private static String prettyStatus(String s){if(s==null||s.isBlank())return "";return switch(s){case"REQUESTED"->"Pending";case"APPROVED"->"Approved — ready for collection";case"REJECTED"->"Not approved";case"ISSUED"->"Issued";case"RETURNED"->"Returned";case"OVERDUE"->"Overdue";case"EXPIRED"->"Approval expired";default->s.substring(0,1)+s.substring(1).toLowerCase();};}
    private static String statusClass(String s){return ("APPROVED".equals(s)||"RETURNED".equals(s)||"ISSUED".equals(s))?"pill-success":"pill-muted";}
    private static String humanAction(String s){if(s==null)return "Activity";String x=s.replace('_',' ').toLowerCase();return Character.toUpperCase(x.charAt(0))+x.substring(1);}

    @Override public void stop(){closeRealtime();}
}
