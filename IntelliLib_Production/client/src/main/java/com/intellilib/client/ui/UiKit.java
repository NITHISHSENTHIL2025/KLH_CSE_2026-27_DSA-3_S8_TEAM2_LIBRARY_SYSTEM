package com.intellilib.client.ui;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.geometry.*;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.util.function.Consumer;

public final class UiKit {
    private UiKit(){}

    public static Label title(String s){Label l=new Label(s);l.getStyleClass().add("h1");return l;}
    public static Label h2(String s){Label l=new Label(s);l.getStyleClass().add("h2");return l;}
    public static Label h3(String s){Label l=new Label(s);l.getStyleClass().add("h3");return l;}
    public static Label muted(String s){Label l=new Label(s);l.getStyleClass().add("muted");l.setWrapText(true);return l;}
    public static Label eyebrow(String s){Label l=new Label(s.toUpperCase());l.getStyleClass().add("eyebrow");return l;}
    public static Button primary(String s){Button b=new Button(s);b.getStyleClass().add("primary-button");return b;}
    public static Button secondary(String s){Button b=new Button(s);b.getStyleClass().add("secondary-button");return b;}
    public static Button danger(String s){Button b=new Button(s);b.getStyleClass().add("danger-button");return b;}
    public static VBox card(Node...n){VBox v=new VBox(10,n);v.getStyleClass().add("card");v.setPadding(new Insets(18));return v;}

    public static void fadeIn(Node n){
        n.setOpacity(0);n.setTranslateY(7);
        Timeline t=new Timeline(new KeyFrame(Duration.millis(190),new KeyValue(n.opacityProperty(),1,Interpolator.EASE_BOTH),new KeyValue(n.translateYProperty(),0,Interpolator.EASE_BOTH)));t.play();
    }

    public static void hoverLift(Node node){
        node.setOnMouseEntered(e->{Timeline t=new Timeline(new KeyFrame(Duration.millis(140),new KeyValue(node.translateYProperty(),-3,Interpolator.EASE_BOTH),new KeyValue(node.scaleXProperty(),1.008),new KeyValue(node.scaleYProperty(),1.008)));t.play();});
        node.setOnMouseExited(e->{Timeline t=new Timeline(new KeyFrame(Duration.millis(150),new KeyValue(node.translateYProperty(),0,Interpolator.EASE_BOTH),new KeyValue(node.scaleXProperty(),1),new KeyValue(node.scaleYProperty(),1)));t.play();});
    }

    public static void toast(StackPane root,String text){
        Platform.runLater(()->{
            Label l=new Label(text);l.getStyleClass().add("toast");l.setWrapText(true);l.setMaxWidth(430);
            StackPane.setAlignment(l,Pos.TOP_RIGHT);StackPane.setMargin(l,new Insets(22));root.getChildren().add(l);l.setOpacity(0);l.setTranslateY(-8);
            Timeline in=new Timeline(new KeyFrame(Duration.millis(170),new KeyValue(l.opacityProperty(),1),new KeyValue(l.translateYProperty(),0)));in.setOnFinished(e->{PauseTransition p=new PauseTransition(Duration.seconds(3));p.setOnFinished(x->{Timeline out=new Timeline(new KeyFrame(Duration.millis(160),new KeyValue(l.opacityProperty(),0),new KeyValue(l.translateYProperty(),-6)));out.setOnFinished(y->root.getChildren().remove(l));out.play();});p.play();});in.play();
        });
    }

    public static VBox empty(String title,String message){
        Label t=h3(title);Label m=muted(message);m.setMaxWidth(460);m.setAlignment(Pos.CENTER);
        VBox box=new VBox(9,t,m);box.setAlignment(Pos.CENTER);box.setPadding(new Insets(48));box.getStyleClass().add("empty-state");return box;
    }

    public static VBox statCard(String label,long value){
        Label v=new Label(String.format("%,d",value));v.getStyleClass().add("stat-value");
        VBox box=card(v,muted(label));box.setPrefWidth(190);return box;
    }

    public static void showMessage(StackPane root,String title,String message){
        Button done=primary("Done");
        VBox content=new VBox(10,h2(title),muted(message),done);content.setMaxWidth(470);
        OverlayHandle h=sheet(root,content,470);done.setOnAction(e->h.close());
    }

    public static void showError(StackPane root,String message){
        Button done=primary("OK");
        Label badge=new Label("!");badge.getStyleClass().add("error-badge");
        VBox content=new VBox(12,badge,h2("Something needs attention"),muted(message==null?"Unexpected error":message),done);content.setMaxWidth(490);
        OverlayHandle h=sheet(root,content,490);done.setOnAction(e->h.close());
    }

    public static void confirm(StackPane root,String title,String message,String confirmText,Runnable confirmed){
        Button cancel=secondary("Cancel"),yes=primary(confirmText);HBox buttons=new HBox(9,cancel,yes);buttons.setAlignment(Pos.CENTER_RIGHT);
        VBox content=new VBox(12,h2(title),muted(message),buttons);content.setMaxWidth(520);
        OverlayHandle h=sheet(root,content,520);cancel.setOnAction(e->h.close());yes.setOnAction(e->{h.close();confirmed.run();});
    }

    public static OverlayHandle sheet(StackPane root,Node content,double maxWidth){
        StackPane overlay=new StackPane();
        Region backdrop=new Region();
        backdrop.getStyleClass().add("overlay-backdrop");
        backdrop.prefWidthProperty().bind(root.widthProperty());
        backdrop.prefHeightProperty().bind(root.heightProperty());

        ScrollPane scroller=new ScrollPane(content);
        scroller.setFitToWidth(true);
        scroller.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroller.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroller.setPannable(false);
        scroller.getStyleClass().add("sheet-scroll");

        VBox shell=new VBox(scroller);
        shell.setPadding(new Insets(24));
        shell.setMaxWidth(maxWidth);
        shell.getStyleClass().add("sheet");
        shell.maxHeightProperty().bind(root.heightProperty().subtract(48));
        VBox.setVgrow(scroller,Priority.ALWAYS);

        overlay.getChildren().addAll(backdrop,shell);
        StackPane.setAlignment(shell,Pos.CENTER);
        StackPane.setMargin(shell,new Insets(24));
        root.getChildren().add(overlay);

        overlay.setOpacity(0);
        shell.setScaleX(.985);
        shell.setScaleY(.985);
        OverlayHandle handle=new OverlayHandle(root,overlay,shell);
        overlay.setFocusTraversable(true);
        overlay.setOnKeyPressed(e->{if(e.getCode()==javafx.scene.input.KeyCode.ESCAPE)handle.close();});
        Timeline in=new Timeline(new KeyFrame(Duration.millis(170),
                new KeyValue(overlay.opacityProperty(),1,Interpolator.EASE_BOTH),
                new KeyValue(shell.scaleXProperty(),1,Interpolator.EASE_BOTH),
                new KeyValue(shell.scaleYProperty(),1,Interpolator.EASE_BOTH)));
        in.play();
        Platform.runLater(overlay::requestFocus);
        return handle;
    }

    public static final class OverlayHandle{
        private final StackPane root,overlay;private final Node shell;private boolean closed;
        OverlayHandle(StackPane root,StackPane overlay,Node shell){this.root=root;this.overlay=overlay;this.shell=shell;}
        public void close(){if(closed)return;closed=true;Timeline out=new Timeline(new KeyFrame(Duration.millis(130),new KeyValue(overlay.opacityProperty(),0),new KeyValue(shell.scaleXProperty(),.99),new KeyValue(shell.scaleYProperty(),.99)));out.setOnFinished(e->root.getChildren().remove(overlay));out.play();}
    }
}
