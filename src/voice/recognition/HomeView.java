/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package voice.recognition;

import edu.cmu.sphinx.api.Configuration;
import edu.cmu.sphinx.api.LiveSpeechRecognizer;
import edu.cmu.sphinx.api.SpeechResult;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Port;
import javax.swing.JOptionPane;

//Mongo imports
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import org.bson.Document;
//Webcam
import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamPanel;


import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Date;
import java.util.Scanner;
import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JFileChooser;

/**
 *
 * @author guitart
 */

public class HomeView extends javax.swing.JFrame {
    LiveSpeechRecognizer recognizer;
    Thread speechThread;
    Thread resourcesThread;
    Logger logger;
    private ImageIcon defaultIcon = new ImageIcon(getClass().getResource("/voice/recognition/noContact.png"));
    private MongoCollection<Document> contactos;
    private MongoCollection<Document> usuarios;
    private MongoCollection<Document> bitacora;
    private String numberData;
    private int errCount = 0;
    private Document selectedDocument = new Document();
    private byte[] contactImage;
    /**
     * Creates new form VoiceRecon
     */
    public HomeView() {
        this.logger = Logger.getLogger(this.getClass().getName());
        this.initComponents();
        panel_contacts.setVisible(false);
        panel_modifyContact.setVisible(false);
        panel_msg.setVisible(false);
        panel_numbers.setVisible(false);
        panel_videoCall.setVisible(false);
        this.initSphinx();
        this.initMongo();
        this.minishell();
        this.setLocationRelativeTo(null);
        this.setVisible(false);
    }
    private void initMongo(){
        MongoClientURI connectionString = new MongoClientURI("mongodb://127.0.0.1:27017");
        MongoClient mongoclient = new MongoClient(connectionString);
        MongoDatabase database = mongoclient.getDatabase("voicerecognition");
        contactos = database.getCollection("contactos");
        usuarios = database.getCollection("usuarios");
        bitacora = database.getCollection("bitacora");
    }
    private void initSphinx() {
        Configuration configuration = new Configuration();
        configuration.setAcousticModelPath("./resources/en-us");
        configuration.setDictionaryPath("./resources/personalizado.dict");
        
        configuration.setGrammarPath("./resources/grammars");
        configuration.setGrammarName("grammar");
        configuration.setUseGrammar(true);
        try {
            this.recognizer = new LiveSpeechRecognizer(configuration);
        }
        catch (IOException ex) {
            System.err.println("ERROR: " + ex);
        }
        this.recognizer.startRecognition(true);
        this.startSpeechThread();
        this.startResourcesThread();
    }
    private void speechHypothesis(){
        do {
            SpeechResult speechResult;
            if ((speechResult = this.recognizer.getResult()) != null) {
                this.executeCommand(speechResult.getHypothesis());
                errCount=0;
                continue;
            }
            this.logger.log(Level.INFO, "I can't understand what you said.\n");
        } while (true);
    }

    private void startSpeechThread() {
        if (this.speechThread != null && this.speechThread.isAlive()) {
            return;
        }
        this.speechThread = new Thread(() -> {
            System.out.println("You can start to speak...");
            
            try {
                speechHypothesis();
            }
            catch (Exception ex) {
                errCount++;
                this.logger.log(Level.WARNING, null, ex);
                this.logger.log(Level.INFO, "SpeechThread has exited...");
                if(errCount > 10)
                    return;
                else
                    speechHypothesis();
            }
            
            
        }
        );
        this.speechThread.start();
    }

    private void startResourcesThread() {
        if (this.resourcesThread != null && this.resourcesThread.isAlive()) {
            return;
        }
        this.resourcesThread = new Thread(() -> {
            try {
                do {
                    if (AudioSystem.isLineSupported(Port.Info.MICROPHONE)) {
                        // empty if block
                    }
                    Thread.sleep(350);
                } while (true);
            }
            catch (InterruptedException ex) {
                this.logger.log(Level.WARNING, null, ex);
                this.resourcesThread.interrupt();
                return;
            }
        }
        );
        this.resourcesThread.start();
    }

    private void executeCommand(String result) {
        if (!result.equalsIgnoreCase("<unk>") && result.length() > 0) {
            System.out.println("Log: " + result);
            String[] tokens = result.split(" ");
            if (this.panel_login.isVisible()) {
                switch (tokens[0]) {
                    case "salir": {
                        guardarBitacora(tokens);
                        System.exit(0);
                    }
                    case "usuario": {
                        this.text_username.setText(tokens[1]);
                        guardarBitacora(tokens);
                        break;
                    }
                    case "contrase\u00f1a": {
                        this.text_password.setText(tokens[1]);
                        guardarBitacora(tokens);
                        break;
                    }
                    case "continuar":
                    case "ingresar":{
                        Document user = new Document("user",text_password.getText()).append("pass", text_username.getText());
                        
                        user = usuarios.find(user).first();
                        
                        if(user.isEmpty()){
                            JOptionPane.showMessageDialog(null, "Usuario o Contraseña Invalida");                            
                        }else{
                            panel_login.setVisible(false);
                            panel_contacts.setVisible(true);
                            panel_modifyContact.setVisible(false);
                            panel_msg.setVisible(false);
                            panel_numbers.setVisible(false);
                            panel_videoCall.setVisible(false);
                            panel_sms.setVisible(false);
                            guardarBitacora(tokens);
                            executeCommand("listar");
                        }
                        break;
                    }
                }
            } else {
                switch (tokens[0]) {
                    case "salir": {
                        guardarBitacora(tokens);
                        System.exit(0);
                    }
                    case "listar":{
                        panel_login.setVisible(false);
                        panel_contacts.setVisible(true);
                        panel_contact.setVisible(false);
                        panel_msg.setVisible(false);
                        panel_numbers.setVisible(false);
                        panel_videoCall.setVisible(false);
                        panel_comandos.setVisible(false);
                        panel_sms.setVisible(false);
                        label_contactList.setText("");
                        Document doc = new Document();
                        if (tokens.length > 1) 
                            doc = new Document("nombre",tokens[1]);
                        if(tokens.length > 2)
                            doc.append("apellido", tokens[2]);
                        MongoCursor<Document> listaContactos = contactos.find(doc).iterator();
                        String listData = "";
                        while(listaContactos.hasNext()){
                            listData += "<li> nombre: " + listaContactos.next().get("nombre",String.class) + "</li>";
                        }
                        label_contactList.setText("<html><ul>"+listData+"</ul></html>");
                        guardarBitacora(tokens);
                        break;
                    }
                    case "abrir": 
                    case "buscar":
                    case "contacto": {
                        panel_login.setVisible(false);
                        panel_contacts.setVisible(false);
                        panel_contact.setVisible(true);
                        panel_msg.setVisible(false);
                        panel_numbers.setVisible(false);
                        panel_videoCall.setVisible(false);
                        panel_comandos.setVisible(false);
                        panel_sms.setVisible(false);
                        Document doc = new Document();
                        if (tokens.length > 1) 
                            doc.append("nombre",tokens[1]);
                        if(tokens.length > 2)
                            doc.append("apellido", tokens[2]);
                        selectedDocument = contactos.find(doc).first();
                        if(selectedDocument == null || selectedDocument.isEmpty())
                            break;
                        if(selectedDocument.getString("nombre") != null)
                            text_contactNombre.setText(selectedDocument.getString("nombre"));
                        if(selectedDocument.getString("apellido") != null)
                         text_contactApellido.setText(selectedDocument.getString("apellido"));
                        if(selectedDocument.getInteger("numero") != null)
                            text_contactNumero.setValue(selectedDocument.getInteger("numero"));
                        if(selectedDocument.getString("imagen") != null){
                            byte[] base64img = Base64.getDecoder().decode(selectedDocument.getString("imagen"));
                            label_contactImage.setIcon(new ImageIcon(base64img));
                        }
                        guardarBitacora(tokens);
                        break;
                    }
                    case "menu": 
                    case "regresar": 
                    case "atras": 
                    case "home": 
                    case "inicio":{
                        panel_login.setVisible(false);
                        panel_contacts.setVisible(false);
                        panel_contact.setVisible(false);
                        panel_msg.setVisible(false);
                        panel_numbers.setVisible(true);
                        panel_videoCall.setVisible(false);
                        panel_comandos.setVisible(false);
                        panel_sms.setVisible(false);
                        guardarBitacora(tokens);
                        //executeCommand("listar");
                        break;
                    }
                    case "llamar":{
                        if (tokens.length > 1){
                            panel_login.setVisible(false);
                            panel_contacts.setVisible(false);
                            panel_contact.setVisible(false);
                            panel_msg.setVisible(false);
                            panel_numbers.setVisible(false);
                            panel_videoCall.setVisible(true);
                            panel_comandos.setVisible(false);
                            panel_sms.setVisible(false);
                                                       
                            Document doc = new Document();
                            doc.append("nombre", tokens[1]);
                            selectedDocument = contactos.find(doc).first();
                            if(selectedDocument == null || selectedDocument.isEmpty())
                                break;

                            String nombre, apellido;
                            if((nombre=selectedDocument.getString("nombre")) != null && (apellido=selectedDocument.getString("apellido")) != null){
                                label_nombreCompleto.setText(nombre+" "+apellido);
                                text_contactApellido.setText(selectedDocument.getString("apellido"));
                            }
                            if(selectedDocument.getInteger("numero") != null)
                                label_contactNumero.setText(selectedDocument.getInteger("numero")+"");
                            if(selectedDocument.getString("imagen") != null){
                                byte[] base64img = Base64.getDecoder().decode(selectedDocument.getString("imagen"));
                                label_video.setIcon(new ImageIcon(base64img));
                            }
                            guardarBitacora(tokens);
                        }
                        
                        break;
                    }
                    case "activar":{
                        if(tokens.length > 1 && tokens[1].equals("video")){
                            activarVideo();
                            label_video.setVisible(false);
                            guardarBitacora(tokens);
                        }
                        break;
                    }
                    case "colgar":{
                        ((WebcamPanel)webCamPanel).stop();
                        guardarBitacora(tokens);
                        executeCommand("home");
                        break;
                    }
                    case "escribir":
                    case "enviar":{
                        if(tokens.length>1){
                            panel_login.setVisible(false);
                            panel_contacts.setVisible(false);
                            panel_modifyContact.setVisible(false);
                            panel_msg.setVisible(false);
                            panel_numbers.setVisible(false);
                            panel_videoCall.setVisible(false);
                            panel_comandos.setVisible(false);
                            panel_sms.setVisible(true);
                            textArea_listaMensajes.setText("");
                            guardarBitacora(tokens);
                            break;
                        }
                        String msg = text_msgToSend.getText();
                        textArea_listaMensajes.append("Yo: " + msg+"\n");
                        text_msgToSend.setText("");
                        guardarBitacora(tokens,"mensaje",msg);
                        break;
                    }
                    case "comandos":{
                        panel_login.setVisible(false);
                        panel_contacts.setVisible(false);
                        panel_modifyContact.setVisible(false);
                        panel_msg.setVisible(false);
                        panel_numbers.setVisible(false);
                        panel_videoCall.setVisible(false);
                        panel_comandos.setVisible(true);
                        panel_sms.setVisible(false);
                        guardarBitacora(tokens);
                        break;
                    }
                    case "crear":{
                        if(tokens.length>1 && tokens[1].equals("contacto")){
                            panel_login.setVisible(false);
                            panel_contacts.setVisible(false);
                            panel_contact.setVisible(true);
                            panel_msg.setVisible(false);
                            panel_numbers.setVisible(false);
                            panel_videoCall.setVisible(false);
                            panel_comandos.setVisible(false);
                            panel_sms.setVisible(false);
                            numberData="";
                            guardarBitacora(tokens);
                        }
                        break;
                    }
                    case "nombre":{
                        if(tokens.length > 1){
                            text_contactNombre.setText(tokens[1]);
                            guardarBitacora(tokens);
                        }
                        
                        break;
                    }
                    case "apellido":{
                        if(tokens.length > 1){
                            text_contactApellido.setText(tokens[1]);
                            guardarBitacora(tokens);
                        }
                        break;
                    }
                    case "numero":{
                        numberData = "";
                        for (int i = 1; i < tokens.length; i++) {
                            numberData+=tokens[i];
                        }
                        text_contactNumero.setValue(Integer.parseInt(numberData));
                        numberData = "";
                        guardarBitacora(tokens);
                        break;
                    }
                    case "cambiar":{
                        if(tokens.length == 2 && tokens[1].equals("imagen")){
                            JFileChooser jfc = new JFileChooser();
                            jfc.showOpenDialog(this);
                            String pathstr = jfc.getSelectedFile().getAbsolutePath();
                            byte[] contactImageData = null;
                            try {
                                contactImageData = Files.readAllBytes(Paths.get(pathstr));
                            } catch (IOException ex) {
                                Logger.getLogger(HomeView.class.getName()).log(Level.SEVERE, null, ex);
                            }
                            if(contactImageData == null){
                                label_contactImage.setIcon(defaultIcon);
                            }else{
                                contactImage=contactImageData;
                                label_contactImage.setIcon(new ImageIcon(contactImageData));
                            }
                            guardarBitacora(tokens);
                            break;
                        }
                        break;
                    }
                    case "guardar":{
                        if(tokens.length > 1){
                            //ICON TO BASE64
                            String base64img = Base64.getEncoder().encodeToString(contactImage);
                            //END ICON TO BASE64
                            Document newDoc = new Document();
                            newDoc
                                .append("nombre",text_contactNombre.getText())
                                .append("apellido", text_contactApellido.getText())
                                .append("numero",text_contactNumero.getValue())
                                .append("imagen",base64img);
                            text_contactNombre.setText("");
                            text_contactApellido.setText("");
                            label_contactImage.setIcon(defaultIcon);
                            if((selectedDocument==null || !selectedDocument.isEmpty()) && tokens.length == 2 && tokens[1].equals("contacto")){
                                System.out.println(selectedDocument);
                                contactos.findOneAndDelete(selectedDocument);
                                contactos.insertOne(newDoc);
                                selectedDocument = newDoc;
                            }else if((selectedDocument==null || !selectedDocument.isEmpty()) && tokens.length == 2 && tokens[1].equals("nuevo")){
                                contactos.insertOne(newDoc);
                                System.out.println("Se agrego a la base de datos :)");
                            }
                            selectedDocument.clear();
                            guardarBitacora(tokens);
                            executeCommand("menu");
                            break;
                        }else{
                            if(text_contactApellido.getText().isEmpty() || text_contactNombre.getText().isEmpty() || text_contactNumero.getValue().equals("")){
                                JOptionPane.showMessageDialog(null, "Pofavor ingrese todos los valores");
                                break;
                            }
                        }
                        break;
                    }
                    case "eliminar":{
                        if(!selectedDocument.isEmpty() && tokens.length == 2 && tokens[1].equals("contacto") && panel_contact.isVisible()){
                            System.out.println("ELIMINANDO");
                            System.out.println(selectedDocument);
                            contactos.findOneAndDelete(selectedDocument);
                            selectedDocument.clear();
                            break;
                        }
                        guardarBitacora(tokens);
                        System.out.println("NO SE REALIZO ELIMINAR");
                        break;
                    }
                }
            }
        }
    }
    private void activarVideo(){
        ((WebcamPanel)webCamPanel).setFPSDisplayed(true);
        ((WebcamPanel)webCamPanel).start();
    }
    private void guardarBitacora(String[] token) {
        Document bitaDoc = new Document();
        bitaDoc.append("comando", token[0]);
        String opciones = "";
        for(int i = 1; i<token.length;i++)
            opciones += token[i] + " ";
        if(opciones.length()>0)
            bitaDoc.append("opciones", opciones);
        bitaDoc.append("fecha",new Date().toString());
        bitacora.insertOne(bitaDoc);
    }
    private void guardarBitacora(String[] token, String key, String value) {
        Document bitaDoc = new Document();
        bitaDoc.append("comando", token[0]);
        String opciones = "";
        for(int i = 1; i<token.length;i++)
            opciones += token[i] + " ";
        if(opciones.length()>0)
            bitaDoc.append("opciones", opciones);
        bitaDoc.append(key, value);
        bitaDoc.append("fecha",new Date().toString());
        bitacora.insertOne(bitaDoc);
    }
    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        panel_login = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        text_username = new javax.swing.JTextField();
        text_password = new javax.swing.JTextField();
        loginButton_salir = new javax.swing.JButton();
        loginButton_login = new javax.swing.JButton();
        panel_msg = new javax.swing.JPanel();
        msgLabel_msg = new javax.swing.JLabel();
        jButton1 = new javax.swing.JButton();
        panel_numbers = new javax.swing.JPanel();
        jTextField1 = new javax.swing.JTextField();
        jButton2 = new javax.swing.JButton();
        jButton3 = new javax.swing.JButton();
        jPanel19 = new javax.swing.JPanel();
        jPanel14 = new javax.swing.JPanel();
        jLabel16 = new javax.swing.JLabel();
        jPanel1 = new javax.swing.JPanel();
        jLabel3 = new javax.swing.JLabel();
        jPanel10 = new javax.swing.JPanel();
        jLabel12 = new javax.swing.JLabel();
        jPanel4 = new javax.swing.JPanel();
        jLabel6 = new javax.swing.JLabel();
        jPanel15 = new javax.swing.JPanel();
        jLabel17 = new javax.swing.JLabel();
        jPanel16 = new javax.swing.JPanel();
        jLabel18 = new javax.swing.JLabel();
        jPanel7 = new javax.swing.JPanel();
        jLabel9 = new javax.swing.JLabel();
        jPanel17 = new javax.swing.JPanel();
        jLabel19 = new javax.swing.JLabel();
        jPanel8 = new javax.swing.JPanel();
        jLabel10 = new javax.swing.JLabel();
        jPanel18 = new javax.swing.JPanel();
        jLabel20 = new javax.swing.JLabel();
        jPanel13 = new javax.swing.JPanel();
        jLabel15 = new javax.swing.JLabel();
        jPanel9 = new javax.swing.JPanel();
        jLabel11 = new javax.swing.JLabel();
        panel_contacts = new javax.swing.JPanel();
        jLabel22 = new javax.swing.JLabel();
        label_contactList = new javax.swing.JLabel();
        panel_modifyContact = new javax.swing.JPanel();
        panel_videoCall = new javax.swing.JPanel();
        jLabel8 = new javax.swing.JLabel();
        label_nombreCompleto = new javax.swing.JLabel();
        jButton6 = new javax.swing.JButton();
        jToggleButton1 = new javax.swing.JToggleButton();
        label_contactNumero = new javax.swing.JLabel();
        Webcam webcam = Webcam.getDefault();
        if(webcam == null){
            System.err.print("no web cam found");
        }
        webCamPanel = new javax.swing.JPanel();
        if(webcam != null){
            webCamPanel = new WebcamPanel(webcam,false);
        }
        label_video = new javax.swing.JLabel();
        panel_contact = new javax.swing.JPanel();
        panel_contact.setVisible(false);
        jLabel4 = new javax.swing.JLabel();
        jLabel5 = new javax.swing.JLabel();
        jLabel7 = new javax.swing.JLabel();
        text_contactNombre = new javax.swing.JTextField();
        text_contactApellido = new javax.swing.JTextField();
        label_contactImage = new javax.swing.JLabel();
        jButton4 = new javax.swing.JButton();
        jButton5 = new javax.swing.JButton();
        text_contactNumero = new javax.swing.JFormattedTextField();
        panel_comandos = new javax.swing.JPanel();
        jLabel21 = new javax.swing.JLabel();
        panel_sms = new javax.swing.JPanel();
        panel_sms.setVisible(false);
        jButton7 = new javax.swing.JButton();
        jScrollPane1 = new javax.swing.JScrollPane();
        textArea_listaMensajes = new javax.swing.JTextArea();
        text_msgToSend = new javax.swing.JTextField();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        getContentPane().setLayout(new org.netbeans.lib.awtextra.AbsoluteLayout());

        jLabel1.setFont(new java.awt.Font("Ubuntu", 1, 24)); // NOI18N
        jLabel1.setText("Usuario");

        jLabel2.setFont(new java.awt.Font("Ubuntu", 1, 24)); // NOI18N
        jLabel2.setText("Contraseña");

        text_username.setText("username");

        text_password.setForeground(new java.awt.Color(255, 15, 0));
        text_password.setText("password");
        text_password.setToolTipText("");
        text_password.setSelectionColor(java.awt.Color.white);

        loginButton_salir.setText("Salir");

        loginButton_login.setText("Continuar");

        javax.swing.GroupLayout panel_loginLayout = new javax.swing.GroupLayout(panel_login);
        panel_login.setLayout(panel_loginLayout);
        panel_loginLayout.setHorizontalGroup(
            panel_loginLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panel_loginLayout.createSequentialGroup()
                .addGap(85, 85, 85)
                .addGroup(panel_loginLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(loginButton_login, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(text_username)
                    .addComponent(text_password)
                    .addComponent(loginButton_salir, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jLabel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jLabel2, javax.swing.GroupLayout.PREFERRED_SIZE, 226, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(99, Short.MAX_VALUE))
        );
        panel_loginLayout.setVerticalGroup(
            panel_loginLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panel_loginLayout.createSequentialGroup()
                .addGap(73, 73, 73)
                .addComponent(jLabel1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(text_username, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(27, 27, 27)
                .addComponent(jLabel2)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(text_password, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(loginButton_login)
                .addGap(18, 18, 18)
                .addComponent(loginButton_salir)
                .addContainerGap(94, Short.MAX_VALUE))
        );

        getContentPane().add(panel_login, new org.netbeans.lib.awtextra.AbsoluteConstraints(0, 0, 410, 410));

        msgLabel_msg.setFont(new java.awt.Font("Ubuntu", 0, 24)); // NOI18N
        msgLabel_msg.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        msgLabel_msg.setText("<html>Mostrar mensaje para guiar al usuario</html>");
        msgLabel_msg.setToolTipText("");

        jButton1.setText("OK");

        javax.swing.GroupLayout panel_msgLayout = new javax.swing.GroupLayout(panel_msg);
        panel_msg.setLayout(panel_msgLayout);
        panel_msgLayout.setHorizontalGroup(
            panel_msgLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panel_msgLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(panel_msgLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(msgLabel_msg, javax.swing.GroupLayout.PREFERRED_SIZE, 368, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jButton1, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(30, Short.MAX_VALUE))
        );
        panel_msgLayout.setVerticalGroup(
            panel_msgLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panel_msgLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(msgLabel_msg, javax.swing.GroupLayout.PREFERRED_SIZE, 218, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(jButton1)
                .addContainerGap(133, Short.MAX_VALUE))
        );

        getContentPane().add(panel_msg, new org.netbeans.lib.awtextra.AbsoluteConstraints(0, 0, 410, 410));

        jTextField1.setFont(new java.awt.Font("Ubuntu", 1, 24)); // NOI18N
        jTextField1.setHorizontalAlignment(javax.swing.JTextField.CENTER);
        jTextField1.setText("97283421");

        jButton2.setText("Llamar");

        jButton3.setText("Contactos");

        jPanel14.setBackground(new java.awt.Color(173, 192, 254));

        jLabel16.setFont(new java.awt.Font("Ubuntu", 1, 24)); // NOI18N
        jLabel16.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel16.setText("0");

        javax.swing.GroupLayout jPanel14Layout = new javax.swing.GroupLayout(jPanel14);
        jPanel14.setLayout(jPanel14Layout);
        jPanel14Layout.setHorizontalGroup(
            jPanel14Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel14Layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jLabel16, javax.swing.GroupLayout.PREFERRED_SIZE, 34, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
        jPanel14Layout.setVerticalGroup(
            jPanel14Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel14Layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jLabel16, javax.swing.GroupLayout.PREFERRED_SIZE, 28, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        jPanel1.setBackground(new java.awt.Color(173, 192, 254));

        jLabel3.setFont(new java.awt.Font("Ubuntu", 1, 24)); // NOI18N
        jLabel3.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel3.setText("1");

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel3, javax.swing.GroupLayout.PREFERRED_SIZE, 34, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel3, javax.swing.GroupLayout.PREFERRED_SIZE, 28, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel10.setBackground(new java.awt.Color(173, 192, 254));

        jLabel12.setFont(new java.awt.Font("Ubuntu", 1, 24)); // NOI18N
        jLabel12.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel12.setText("5");

        javax.swing.GroupLayout jPanel10Layout = new javax.swing.GroupLayout(jPanel10);
        jPanel10.setLayout(jPanel10Layout);
        jPanel10Layout.setHorizontalGroup(
            jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel10Layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jLabel12, javax.swing.GroupLayout.PREFERRED_SIZE, 34, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
        jPanel10Layout.setVerticalGroup(
            jPanel10Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel10Layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jLabel12, javax.swing.GroupLayout.PREFERRED_SIZE, 28, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        jPanel4.setBackground(new java.awt.Color(173, 192, 254));

        jLabel6.setFont(new java.awt.Font("Ubuntu", 1, 24)); // NOI18N
        jLabel6.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel6.setText("2");

        javax.swing.GroupLayout jPanel4Layout = new javax.swing.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel4Layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jLabel6, javax.swing.GroupLayout.PREFERRED_SIZE, 34, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel4Layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jLabel6, javax.swing.GroupLayout.PREFERRED_SIZE, 28, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        jPanel15.setBackground(new java.awt.Color(173, 192, 254));

        jLabel17.setFont(new java.awt.Font("Ubuntu", 1, 24)); // NOI18N
        jLabel17.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel17.setText("8");

        javax.swing.GroupLayout jPanel15Layout = new javax.swing.GroupLayout(jPanel15);
        jPanel15.setLayout(jPanel15Layout);
        jPanel15Layout.setHorizontalGroup(
            jPanel15Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel15Layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jLabel17, javax.swing.GroupLayout.PREFERRED_SIZE, 34, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
        jPanel15Layout.setVerticalGroup(
            jPanel15Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel15Layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jLabel17, javax.swing.GroupLayout.PREFERRED_SIZE, 28, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        jPanel16.setBackground(new java.awt.Color(173, 192, 254));

        jLabel18.setFont(new java.awt.Font("Ubuntu", 1, 24)); // NOI18N
        jLabel18.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel18.setText("9");

        javax.swing.GroupLayout jPanel16Layout = new javax.swing.GroupLayout(jPanel16);
        jPanel16.setLayout(jPanel16Layout);
        jPanel16Layout.setHorizontalGroup(
            jPanel16Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel16Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel18, javax.swing.GroupLayout.PREFERRED_SIZE, 34, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel16Layout.setVerticalGroup(
            jPanel16Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel16Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel18, javax.swing.GroupLayout.PREFERRED_SIZE, 28, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel7.setBackground(new java.awt.Color(173, 192, 254));

        jLabel9.setFont(new java.awt.Font("Ubuntu", 1, 24)); // NOI18N
        jLabel9.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel9.setText("3");

        javax.swing.GroupLayout jPanel7Layout = new javax.swing.GroupLayout(jPanel7);
        jPanel7.setLayout(jPanel7Layout);
        jPanel7Layout.setHorizontalGroup(
            jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel7Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel9, javax.swing.GroupLayout.PREFERRED_SIZE, 34, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel7Layout.setVerticalGroup(
            jPanel7Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel7Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel9, javax.swing.GroupLayout.PREFERRED_SIZE, 28, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel17.setBackground(new java.awt.Color(173, 192, 254));

        jLabel19.setFont(new java.awt.Font("Ubuntu", 1, 24)); // NOI18N
        jLabel19.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel19.setText("#");

        javax.swing.GroupLayout jPanel17Layout = new javax.swing.GroupLayout(jPanel17);
        jPanel17.setLayout(jPanel17Layout);
        jPanel17Layout.setHorizontalGroup(
            jPanel17Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel17Layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jLabel19, javax.swing.GroupLayout.PREFERRED_SIZE, 34, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
        jPanel17Layout.setVerticalGroup(
            jPanel17Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel17Layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jLabel19, javax.swing.GroupLayout.PREFERRED_SIZE, 28, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        jPanel8.setBackground(new java.awt.Color(173, 192, 254));

        jLabel10.setFont(new java.awt.Font("Ubuntu", 1, 24)); // NOI18N
        jLabel10.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel10.setText("6");

        javax.swing.GroupLayout jPanel8Layout = new javax.swing.GroupLayout(jPanel8);
        jPanel8.setLayout(jPanel8Layout);
        jPanel8Layout.setHorizontalGroup(
            jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel8Layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jLabel10, javax.swing.GroupLayout.PREFERRED_SIZE, 34, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
        jPanel8Layout.setVerticalGroup(
            jPanel8Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel8Layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jLabel10, javax.swing.GroupLayout.PREFERRED_SIZE, 28, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        jPanel18.setBackground(new java.awt.Color(173, 192, 254));

        jLabel20.setFont(new java.awt.Font("Ubuntu", 1, 24)); // NOI18N
        jLabel20.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel20.setText("*");

        javax.swing.GroupLayout jPanel18Layout = new javax.swing.GroupLayout(jPanel18);
        jPanel18.setLayout(jPanel18Layout);
        jPanel18Layout.setHorizontalGroup(
            jPanel18Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel18Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel20, javax.swing.GroupLayout.PREFERRED_SIZE, 34, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel18Layout.setVerticalGroup(
            jPanel18Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel18Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel20, javax.swing.GroupLayout.PREFERRED_SIZE, 28, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel13.setBackground(new java.awt.Color(173, 192, 254));

        jLabel15.setFont(new java.awt.Font("Ubuntu", 1, 24)); // NOI18N
        jLabel15.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel15.setText("7");

        javax.swing.GroupLayout jPanel13Layout = new javax.swing.GroupLayout(jPanel13);
        jPanel13.setLayout(jPanel13Layout);
        jPanel13Layout.setHorizontalGroup(
            jPanel13Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel13Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel15, javax.swing.GroupLayout.PREFERRED_SIZE, 34, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        jPanel13Layout.setVerticalGroup(
            jPanel13Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel13Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel15, javax.swing.GroupLayout.PREFERRED_SIZE, 28, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        jPanel9.setBackground(new java.awt.Color(173, 192, 254));

        jLabel11.setFont(new java.awt.Font("Ubuntu", 1, 24)); // NOI18N
        jLabel11.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        jLabel11.setText("4");

        javax.swing.GroupLayout jPanel9Layout = new javax.swing.GroupLayout(jPanel9);
        jPanel9.setLayout(jPanel9Layout);
        jPanel9Layout.setHorizontalGroup(
            jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel9Layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jLabel11, javax.swing.GroupLayout.PREFERRED_SIZE, 34, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );
        jPanel9Layout.setVerticalGroup(
            jPanel9Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel9Layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jLabel11, javax.swing.GroupLayout.PREFERRED_SIZE, 28, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap())
        );

        javax.swing.GroupLayout jPanel19Layout = new javax.swing.GroupLayout(jPanel19);
        jPanel19.setLayout(jPanel19Layout);
        jPanel19Layout.setHorizontalGroup(
            jPanel19Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel19Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel19Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel19Layout.createSequentialGroup()
                        .addGroup(jPanel19Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jPanel13, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jPanel18, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(18, 18, 18)
                        .addGroup(jPanel19Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel19Layout.createSequentialGroup()
                                .addComponent(jPanel15, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(jPanel16, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(jPanel19Layout.createSequentialGroup()
                                .addComponent(jPanel14, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(18, 18, 18)
                                .addComponent(jPanel17, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(jPanel19Layout.createSequentialGroup()
                        .addGroup(jPanel19Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jPanel9, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(18, 18, 18)
                        .addGroup(jPanel19Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jPanel10, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jPanel4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(18, 18, 18)
                        .addGroup(jPanel19Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jPanel8, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jPanel7, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
        );
        jPanel19Layout.setVerticalGroup(
            jPanel19Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel19Layout.createSequentialGroup()
                .addGap(18, 18, 18)
                .addGroup(jPanel19Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanel4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jPanel7, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(18, 18, 18)
                .addGroup(jPanel19Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel19Layout.createSequentialGroup()
                        .addGroup(jPanel19Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jPanel10, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jPanel8, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                        .addGap(18, 18, 18)
                        .addGroup(jPanel19Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(jPanel15, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jPanel16, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                    .addGroup(jPanel19Layout.createSequentialGroup()
                        .addComponent(jPanel9, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(18, 18, 18)
                        .addComponent(jPanel13, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addGap(18, 18, 18)
                .addGroup(jPanel19Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanel18, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jPanel14, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jPanel17, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout panel_numbersLayout = new javax.swing.GroupLayout(panel_numbers);
        panel_numbers.setLayout(panel_numbersLayout);
        panel_numbersLayout.setHorizontalGroup(
            panel_numbersLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panel_numbersLayout.createSequentialGroup()
                .addGap(80, 80, 80)
                .addGroup(panel_numbersLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                    .addComponent(jPanel19, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jTextField1, javax.swing.GroupLayout.PREFERRED_SIZE, 222, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jButton3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jButton2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addContainerGap(96, Short.MAX_VALUE))
        );
        panel_numbersLayout.setVerticalGroup(
            panel_numbersLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, panel_numbersLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jTextField1, javax.swing.GroupLayout.PREFERRED_SIZE, 50, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jPanel19, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jButton2)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jButton3)
                .addContainerGap())
        );

        getContentPane().add(panel_numbers, new org.netbeans.lib.awtextra.AbsoluteConstraints(0, 0, 410, 410));

        jLabel22.setBackground(new java.awt.Color(254, 205, 248));
        jLabel22.setText("<html><h1>Contacts</h1></html>");
        jLabel22.setVerticalAlignment(javax.swing.SwingConstants.TOP);
        jLabel22.setOpaque(true);

        label_contactList.setText("<html>\n  <ul>\n    <li>hola</li>\n  <ul>\n</html>");

        javax.swing.GroupLayout panel_contactsLayout = new javax.swing.GroupLayout(panel_contacts);
        panel_contacts.setLayout(panel_contactsLayout);
        panel_contactsLayout.setHorizontalGroup(
            panel_contactsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, panel_contactsLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(panel_contactsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addComponent(label_contactList)
                    .addComponent(jLabel22, javax.swing.GroupLayout.DEFAULT_SIZE, 386, Short.MAX_VALUE))
                .addContainerGap())
        );
        panel_contactsLayout.setVerticalGroup(
            panel_contactsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panel_contactsLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel22, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(label_contactList, javax.swing.GroupLayout.DEFAULT_SIZE, 326, Short.MAX_VALUE)
                .addContainerGap())
        );

        getContentPane().add(panel_contacts, new org.netbeans.lib.awtextra.AbsoluteConstraints(0, 0, 410, 410));

        javax.swing.GroupLayout panel_modifyContactLayout = new javax.swing.GroupLayout(panel_modifyContact);
        panel_modifyContact.setLayout(panel_modifyContactLayout);
        panel_modifyContactLayout.setHorizontalGroup(
            panel_modifyContactLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 410, Short.MAX_VALUE)
        );
        panel_modifyContactLayout.setVerticalGroup(
            panel_modifyContactLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 410, Short.MAX_VALUE)
        );

        getContentPane().add(panel_modifyContact, new org.netbeans.lib.awtextra.AbsoluteConstraints(0, 0, 410, 410));

        jLabel8.setText("LLamanda en progreso");

        label_nombreCompleto.setText("Nombre Apellido");

        jButton6.setText("Colgar");

        jToggleButton1.setText("Video llamada");

        label_contactNumero.setText("numero");

        label_video.setIcon(new javax.swing.ImageIcon(getClass().getResource("/voice/recognition/noContact.png"))); // NOI18N

        javax.swing.GroupLayout webCamPanelLayout = new javax.swing.GroupLayout(webCamPanel);
        webCamPanel.setLayout(webCamPanelLayout);
        webCamPanelLayout.setHorizontalGroup(
            webCamPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(webCamPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(label_video, javax.swing.GroupLayout.PREFERRED_SIZE, 156, Short.MAX_VALUE)
                .addContainerGap())
        );
        webCamPanelLayout.setVerticalGroup(
            webCamPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(webCamPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(label_video, javax.swing.GroupLayout.PREFERRED_SIZE, 156, Short.MAX_VALUE)
                .addContainerGap())
        );

        javax.swing.GroupLayout panel_videoCallLayout = new javax.swing.GroupLayout(panel_videoCall);
        panel_videoCall.setLayout(panel_videoCallLayout);
        panel_videoCallLayout.setHorizontalGroup(
            panel_videoCallLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panel_videoCallLayout.createSequentialGroup()
                .addGap(112, 112, 112)
                .addGroup(panel_videoCallLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(webCamPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(panel_videoCallLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                        .addComponent(jLabel8, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(label_nombreCompleto, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(jButton6, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(jToggleButton1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(label_contactNumero, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                .addContainerGap(118, Short.MAX_VALUE))
        );
        panel_videoCallLayout.setVerticalGroup(
            panel_videoCallLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panel_videoCallLayout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(webCamPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(jLabel8)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(label_nombreCompleto)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(label_contactNumero)
                .addGap(15, 15, 15)
                .addComponent(jToggleButton1)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jButton6)
                .addGap(65, 65, 65))
        );

        getContentPane().add(panel_videoCall, new org.netbeans.lib.awtextra.AbsoluteConstraints(0, 0, 410, 410));

        jLabel4.setFont(new java.awt.Font("Ubuntu", 0, 18)); // NOI18N
        jLabel4.setText("Apellido");

        jLabel5.setFont(new java.awt.Font("Ubuntu", 0, 18)); // NOI18N
        jLabel5.setText("Nombre");

        jLabel7.setFont(new java.awt.Font("Ubuntu", 0, 18)); // NOI18N
        jLabel7.setText("Numero");

        label_contactImage.setIcon(new javax.swing.ImageIcon(getClass().getResource("/voice/recognition/noContact.png"))); // NOI18N

        jButton4.setText("Guardar");

        jButton5.setText("Eliminar");

        text_contactNumero.setFormatterFactory(new javax.swing.text.DefaultFormatterFactory(new javax.swing.text.NumberFormatter(new java.text.DecimalFormat("#######"))));

        javax.swing.GroupLayout panel_contactLayout = new javax.swing.GroupLayout(panel_contact);
        panel_contact.setLayout(panel_contactLayout);
        panel_contactLayout.setHorizontalGroup(
            panel_contactLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panel_contactLayout.createSequentialGroup()
                .addGroup(panel_contactLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(panel_contactLayout.createSequentialGroup()
                        .addGap(119, 119, 119)
                        .addComponent(label_contactImage, javax.swing.GroupLayout.PREFERRED_SIZE, 150, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addGroup(panel_contactLayout.createSequentialGroup()
                        .addGap(50, 50, 50)
                        .addGroup(panel_contactLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, panel_contactLayout.createSequentialGroup()
                                .addComponent(jButton4, javax.swing.GroupLayout.PREFERRED_SIZE, 132, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, 36, Short.MAX_VALUE)
                                .addComponent(jButton5, javax.swing.GroupLayout.PREFERRED_SIZE, 132, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addComponent(jLabel5)
                            .addComponent(jLabel7)
                            .addComponent(jLabel4)
                            .addComponent(text_contactNombre)
                            .addComponent(text_contactApellido)
                            .addComponent(text_contactNumero))))
                .addContainerGap(60, Short.MAX_VALUE))
        );
        panel_contactLayout.setVerticalGroup(
            panel_contactLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, panel_contactLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(label_contactImage, javax.swing.GroupLayout.PREFERRED_SIZE, 169, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jLabel5)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(text_contactNombre, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jLabel4)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(text_contactApellido, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jLabel7)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(text_contactNumero, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(8, 8, 8)
                .addGroup(panel_contactLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButton4)
                    .addComponent(jButton5)))
        );

        getContentPane().add(panel_contact, new org.netbeans.lib.awtextra.AbsoluteConstraints(0, 0, 410, 410));

        jLabel21.setBackground(new java.awt.Color(254, 223, 193));
        jLabel21.setText("<html>\n    <h1>Comandos</h1>\n<ul>\n  <li>nuevo [\"contacto\" | nombre]</li>\n  <li>editar (nombre)</li>\n  <li>llamar (nombre)</li>\n  <li>mensaje (nombre)</li>\n  <li>buscar (nombre)</li>\n  <li>contactos </li>\n  <li>guardar</li>\n  <li>comandos</li>\n</ul>\n</html>");
        jLabel21.setVerticalAlignment(javax.swing.SwingConstants.TOP);
        jLabel21.setOpaque(true);

        javax.swing.GroupLayout panel_comandosLayout = new javax.swing.GroupLayout(panel_comandos);
        panel_comandos.setLayout(panel_comandosLayout);
        panel_comandosLayout.setHorizontalGroup(
            panel_comandosLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panel_comandosLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel21, javax.swing.GroupLayout.DEFAULT_SIZE, 386, Short.MAX_VALUE)
                .addContainerGap())
        );
        panel_comandosLayout.setVerticalGroup(
            panel_comandosLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panel_comandosLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel21, javax.swing.GroupLayout.DEFAULT_SIZE, 376, Short.MAX_VALUE)
                .addGap(22, 22, 22))
        );

        getContentPane().add(panel_comandos, new org.netbeans.lib.awtextra.AbsoluteConstraints(0, 0, -1, -1));

        jButton7.setText("Enviar");

        textArea_listaMensajes.setColumns(20);
        textArea_listaMensajes.setRows(5);
        textArea_listaMensajes.setText("Yo: hola\n    Nombre: hola\nYo: que pedos\n   Nombre: hola\n");
        jScrollPane1.setViewportView(textArea_listaMensajes);

        javax.swing.GroupLayout panel_smsLayout = new javax.swing.GroupLayout(panel_sms);
        panel_sms.setLayout(panel_smsLayout);
        panel_smsLayout.setHorizontalGroup(
            panel_smsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panel_smsLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(panel_smsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                    .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 368, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addGroup(panel_smsLayout.createSequentialGroup()
                        .addComponent(text_msgToSend)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jButton7, javax.swing.GroupLayout.PREFERRED_SIZE, 100, javax.swing.GroupLayout.PREFERRED_SIZE)))
                .addContainerGap(30, Short.MAX_VALUE))
        );
        panel_smsLayout.setVerticalGroup(
            panel_smsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(panel_smsLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 338, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(panel_smsLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jButton7)
                    .addComponent(text_msgToSend, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addGap(25, 25, 25))
        );

        getContentPane().add(panel_sms, new org.netbeans.lib.awtextra.AbsoluteConstraints(0, 0, 410, 410));

        pack();
    }// </editor-fold>//GEN-END:initComponents
    void minishell(){
        new Thread(() -> {
            System.out.println("You can start to type...");
            while(true){
                try {
                    System.out.print("$hell:>");
                    Scanner sc = new Scanner(System.in);
                    String command = sc.nextLine();
                    executeCommand(command);
                }
                catch (Exception ex) {
                    this.logger.log(Level.WARNING, null, ex);
                    this.logger.log(Level.INFO, "SpeechThread has exited...");
                }
            }
            
            
        }
        ).start();
    }
    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException ex) {
            java.util.logging.Logger.getLogger(HomeView.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            java.util.logging.Logger.getLogger(HomeView.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(HomeView.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(HomeView.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>
        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new HomeView().setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton jButton1;
    private javax.swing.JButton jButton2;
    private javax.swing.JButton jButton3;
    private javax.swing.JButton jButton4;
    private javax.swing.JButton jButton5;
    private javax.swing.JButton jButton6;
    private javax.swing.JButton jButton7;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel10;
    private javax.swing.JLabel jLabel11;
    private javax.swing.JLabel jLabel12;
    private javax.swing.JLabel jLabel15;
    private javax.swing.JLabel jLabel16;
    private javax.swing.JLabel jLabel17;
    private javax.swing.JLabel jLabel18;
    private javax.swing.JLabel jLabel19;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel20;
    private javax.swing.JLabel jLabel21;
    private javax.swing.JLabel jLabel22;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel10;
    private javax.swing.JPanel jPanel13;
    private javax.swing.JPanel jPanel14;
    private javax.swing.JPanel jPanel15;
    private javax.swing.JPanel jPanel16;
    private javax.swing.JPanel jPanel17;
    private javax.swing.JPanel jPanel18;
    private javax.swing.JPanel jPanel19;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JPanel jPanel7;
    private javax.swing.JPanel jPanel8;
    private javax.swing.JPanel jPanel9;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JTextField jTextField1;
    private javax.swing.JToggleButton jToggleButton1;
    private javax.swing.JLabel label_contactImage;
    private javax.swing.JLabel label_contactList;
    private javax.swing.JLabel label_contactNumero;
    private javax.swing.JLabel label_nombreCompleto;
    private javax.swing.JLabel label_video;
    private javax.swing.JButton loginButton_login;
    private javax.swing.JButton loginButton_salir;
    private javax.swing.JLabel msgLabel_msg;
    private javax.swing.JPanel panel_comandos;
    private javax.swing.JPanel panel_contact;
    private javax.swing.JPanel panel_contacts;
    private javax.swing.JPanel panel_login;
    private javax.swing.JPanel panel_modifyContact;
    private javax.swing.JPanel panel_msg;
    private javax.swing.JPanel panel_numbers;
    private javax.swing.JPanel panel_sms;
    private javax.swing.JPanel panel_videoCall;
    private javax.swing.JTextArea textArea_listaMensajes;
    private javax.swing.JTextField text_contactApellido;
    private javax.swing.JTextField text_contactNombre;
    private javax.swing.JFormattedTextField text_contactNumero;
    private javax.swing.JTextField text_msgToSend;
    private javax.swing.JTextField text_password;
    private javax.swing.JTextField text_username;
    private javax.swing.JPanel webCamPanel;
    // End of variables declaration//GEN-END:variables

    
}
