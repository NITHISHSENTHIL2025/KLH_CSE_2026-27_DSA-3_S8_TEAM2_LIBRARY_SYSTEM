package com.intellilib.client.scanner;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamResolution;
import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** In-app webcam QR/barcode scanner. No external camera window is used. */
public final class CameraScannerOverlay {
    private CameraScannerOverlay() {}

    public static void open(StackPane appRoot, String title, String subtitle, Consumer<String> onScanned) {
        new Session(appRoot,title,subtitle,onScanned).show();
    }

    private static final class Session {
        private final StackPane appRoot;
        private final Consumer<String> onScanned;
        private final StackPane overlay=new StackPane();
        private final ImageView preview=new ImageView();
        private final Label status=new Label("Starting camera…");
        private final ComboBox<WebcamItem> cameraPicker=new ComboBox<>();
        private final AtomicBoolean closed=new AtomicBoolean(false);
        private final AtomicBoolean completed=new AtomicBoolean(false);
        private ScheduledExecutorService executor;
        private Webcam webcam;
        private boolean internalSelection;

        Session(StackPane appRoot,String title,String subtitle,Consumer<String> onScanned){
            this.appRoot=appRoot; this.onScanned=onScanned;
            Region backdrop=new Region();backdrop.getStyleClass().add("overlay-backdrop");backdrop.prefWidthProperty().bind(appRoot.widthProperty());backdrop.prefHeightProperty().bind(appRoot.heightProperty());

            Label heading=new Label(title);heading.getStyleClass().add("sheet-title");
            Label sub=new Label(subtitle);sub.getStyleClass().add("muted");sub.setWrapText(true);
            preview.setFitWidth(620);preview.setFitHeight(360);preview.setPreserveRatio(true);preview.getStyleClass().add("camera-preview");
            StackPane cameraFrame=new StackPane(preview);cameraFrame.setMinSize(620,360);cameraFrame.setPrefSize(620,360);cameraFrame.getStyleClass().add("camera-frame");
            Region guide=new Region();guide.getStyleClass().add("scan-guide");guide.setMouseTransparent(true);guide.setMaxSize(360,190);cameraFrame.getChildren().add(guide);

            cameraPicker.setPromptText("Camera");cameraPicker.setMaxWidth(Double.MAX_VALUE);cameraPicker.setVisible(false);cameraPicker.setManaged(false);
            Button retry=new Button("Retry camera");retry.getStyleClass().add("secondary-button");retry.setOnAction(e->startCamera(cameraPicker.getValue()));
            Button cancel=new Button("Cancel");cancel.getStyleClass().add("secondary-button");cancel.setOnAction(e->close());
            HBox actions=new HBox(10,retry,cancel);actions.setAlignment(Pos.CENTER_RIGHT);
            status.getStyleClass().add("scanner-status");
            VBox card=new VBox(14,heading,sub,cameraPicker,cameraFrame,status,actions);card.setMaxWidth(690);card.getStyleClass().add("scanner-sheet");
            overlay.getChildren().addAll(backdrop,card);StackPane.setAlignment(card,Pos.CENTER);StackPane.setMargin(card,new Insets(30));
            backdrop.setOnMouseClicked(e->{}); // intentional modal backdrop

            cameraPicker.setOnAction(e->{if(internalSelection)return;WebcamItem item=cameraPicker.getValue();if(item!=null)startCamera(item);});
        }

        void show(){
            appRoot.getChildren().add(overlay);
            overlay.setOpacity(0);
            javafx.animation.FadeTransition ft=new javafx.animation.FadeTransition(javafx.util.Duration.millis(150),overlay);ft.setToValue(1);ft.play();
            loadCameras();
        }

        private void loadCameras(){
            status.setText("Looking for a camera…");
            CompletableFuture.runAsync(()->{
                try{
                    List<Webcam> cams=Webcam.getWebcams(8,TimeUnit.SECONDS);
                    Platform.runLater(()->{
                        if(closed.get())return;
                        cameraPicker.getItems().setAll(cams.stream().map(WebcamItem::new).toList());
                        cameraPicker.setVisible(cams.size()>1);cameraPicker.setManaged(cams.size()>1);
                        if(cams.isEmpty()){status.setText("No camera found. Connect a webcam and choose Retry camera.");return;}
                        WebcamItem first=new WebcamItem(cams.getFirst());internalSelection=true;cameraPicker.setValue(first);internalSelection=false;startCamera(first);
                    });
                }catch(Exception e){Platform.runLater(()->status.setText("Camera could not start. Check Windows camera permission and try again."));}
            });
        }

        private synchronized void startCamera(WebcamItem item){
            stopCamera();
            if(item==null){loadCameras();return;}
            status.setText("Starting "+item+"…");
            executor=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"intellilib-camera");t.setDaemon(true);return t;});
            executor.execute(()->{
                try{
                    webcam=item.webcam();
                    Dimension preferred=WebcamResolution.HD.getSize();
                    if(Arrays.asList(webcam.getViewSizes()).contains(preferred))webcam.setViewSize(preferred);
                    else {Dimension vga=WebcamResolution.VGA.getSize();if(Arrays.asList(webcam.getViewSizes()).contains(vga))webcam.setViewSize(vga);}
                    webcam.open(true);
                    Platform.runLater(()->status.setText("Hold the ID or book code inside the frame."));
                    executor.scheduleWithFixedDelay(this::captureAndDecode,0,110,TimeUnit.MILLISECONDS);
                }catch(Exception e){Platform.runLater(()->status.setText("Unable to open camera. Close other apps using it, then Retry camera."));}
            });
        }

        private void captureAndDecode(){
            if(closed.get()||completed.get()||webcam==null||!webcam.isOpen())return;
            try{
                BufferedImage image=webcam.getImage();if(image==null)return;
                var fx=SwingFXUtils.toFXImage(image,null);Platform.runLater(()->preview.setImage(fx));
                String decoded=decode(image);
                if(decoded!=null&&!decoded.isBlank()&&completed.compareAndSet(false,true)){
                    Platform.runLater(()->{
                        status.setText("✓ Code recognized");
                        PauseTransitionHelper.after(180,()->{close();onScanned.accept(decoded.trim());});
                    });
                }
            }catch(Exception ignored){}
        }

        private String decode(BufferedImage image){
            try{
                LuminanceSource source=new BufferedImageLuminanceSource(image);
                BinaryBitmap bitmap=new BinaryBitmap(new HybridBinarizer(source));
                Map<DecodeHintType,Object> hints=new EnumMap<>(DecodeHintType.class);
                hints.put(DecodeHintType.TRY_HARDER,Boolean.TRUE);
                hints.put(DecodeHintType.POSSIBLE_FORMATS,List.of(BarcodeFormat.QR_CODE,BarcodeFormat.CODE_128,BarcodeFormat.CODE_39,BarcodeFormat.CODE_93,BarcodeFormat.EAN_13,BarcodeFormat.EAN_8,BarcodeFormat.UPC_A,BarcodeFormat.UPC_E,BarcodeFormat.ITF,BarcodeFormat.CODABAR,BarcodeFormat.DATA_MATRIX,BarcodeFormat.PDF_417));
                return new MultiFormatReader().decode(bitmap,hints).getText();
            }catch(NotFoundException e){return null;}
        }

        private synchronized void stopCamera(){
            try{if(executor!=null)executor.shutdownNow();}catch(Exception ignored){}
            executor=null;
            try{if(webcam!=null&&webcam.isOpen())webcam.close();}catch(Exception ignored){}
            webcam=null;
        }

        private void close(){
            if(!closed.compareAndSet(false,true))return;
            stopCamera();
            javafx.animation.FadeTransition ft=new javafx.animation.FadeTransition(javafx.util.Duration.millis(120),overlay);ft.setToValue(0);ft.setOnFinished(e->appRoot.getChildren().remove(overlay));ft.play();
        }
    }

    private record WebcamItem(Webcam webcam){@Override public String toString(){return webcam.getName();}}

    private static final class PauseTransitionHelper{
        static void after(int millis,Runnable r){var p=new javafx.animation.PauseTransition(javafx.util.Duration.millis(millis));p.setOnFinished(e->r.run());p.play();}
    }
}
