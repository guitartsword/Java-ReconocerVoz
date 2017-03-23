/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package voice.recognition;
import com.mongodb.MongoClient;
import com.mongodb.MongoClientURI;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoCollection;

import java.util.Arrays;

import com.mongodb.client.MongoCursor;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
/**
 *
 * @author guitart
 */
public class VoiceRecognition {

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        MongoClientURI connectionString = new MongoClientURI("mongodb://127.0.0.1:27017");
        MongoClient mongoclient = new MongoClient(connectionString);
        MongoDatabase database = mongoclient.getDatabase("inscripciones_iise");
        Document doc = new Document("name","Isaias");
        doc.append("type", "database");
        MongoCollection<Document> collection = database.getCollection("students");
        System.out.println(collection.find().first());
        
    }
    
}
