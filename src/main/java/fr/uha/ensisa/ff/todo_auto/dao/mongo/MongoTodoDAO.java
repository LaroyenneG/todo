package fr.uha.ensisa.ff.todo_auto.dao.mongo;

import com.mongodb.MongoWriteException;
import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Projections;
import com.mongodb.client.result.UpdateResult;
import fr.uha.ensisa.ff.todo_auto.dao.TodoDAO;
import fr.uha.ensisa.ff.todo_auto.dao.UnknownListException;
import fr.uha.ensisa.ff.todo_auto.dao.UnknownUserException;
import fr.uha.ensisa.ff.todo_auto.dao.UserAlreadyExistsException;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MongoTodoDAO implements TodoDAO {

    private static final int DOCUMENT_KEY_DUPLICATION_CODE = 11000;

    private static final String CLUSTER_ADDRESS = "mongodb://localhost:27017";

    private static final String DATABASE_NAME = "todo";

    private static final String USER_COLLECTION_NAME = "users";
    private static final String LIST_COLLECTION_NAME = "lists";

    private final MongoClient mongoClient;

    public MongoTodoDAO() {
        this.mongoClient = MongoClients.create(CLUSTER_ADDRESS);
    }

    private MongoDatabase getDatabase() {
        return mongoClient.getDatabase(DATABASE_NAME);
    }

    private MongoCollection<Document> getUsersCollection() {

        MongoDatabase mongoDatabase = getDatabase();

        return mongoDatabase.getCollection(USER_COLLECTION_NAME);
    }

    private MongoCollection<Document> getListsCollection() {

        MongoDatabase mongoDatabase = getDatabase();

        return mongoDatabase.getCollection(LIST_COLLECTION_NAME);
    }


    @Override
    public void registerUser(String user, String password) throws UserAlreadyExistsException {

        Document userDocument = new Document();
        userDocument.append("_id", user);
        userDocument.append("password", password);

        MongoCollection<Document> usersCollection = getUsersCollection();

        try {
            usersCollection.insertOne(userDocument);
        } catch (MongoWriteException e) {
            if (e.getCode() == DOCUMENT_KEY_DUPLICATION_CODE) {
                throw new UserAlreadyExistsException(user);
            } else {
                throw e;
            }
        }
    }

    @Override
    public String getUserPassword(String user) throws UnknownUserException {

        MongoCollection<Document> usersCollection = getUsersCollection();

        Document userDocument = usersCollection.find(Filters.eq("_id", user)).projection(Projections.include("password")).first();

        if (userDocument == null) {
            throw new UnknownUserException(user);
        }

        return userDocument.getString("password");
    }

    @Override
    public String createDefaultTask(String user, String taskName) throws UnknownUserException {


        return null;
    }

    @Override
    public List<Map<String, Object>> getDefaultTasks(String user) throws UnknownUserException {


        return null;
    }

    @Override
    public void setDefaultTaskDone(String user, String taskId, boolean done) throws UnknownUserException {

    }

    @Override
    public void renameDefaultTask(String user, String taskId, String newName) throws UnknownUserException {

    }

    @Override
    public void deleteDefaultTask(String user, String taskId) throws UnknownUserException {

    }

    @Override
    public String createList(String user, String name) throws UnknownUserException {

        MongoCollection<Document> listsCollection = getListsCollection();

        MongoCollection<Document> usersDocument = getUsersCollection();

        Document userDocument = usersDocument.find(Filters.eq("_id", user)).projection(Projections.include("_id")).first();

        if (userDocument == null) {
            throw new UnknownUserException(user);
        }

        Document listDocument = new Document();
        listDocument.put("user", userDocument.getString("_id"));
        listDocument.put("name", name);
        listsCollection.insertOne(listDocument);

        return listDocument.getObjectId("_id").toHexString();
    }

    @Override
    public Map<String, String> getLists(String user) throws UnknownUserException {

        MongoCollection<Document> usersDocument = getUsersCollection();

        Document userDocument = usersDocument.find(Filters.eq("_id", user)).projection(Projections.include("_id")).first();

        if (userDocument == null) {
            throw new UnknownUserException(user);
        }

        MongoCollection<Document> listsCollection = getListsCollection();

        FindIterable<Document> listDocuments = listsCollection.find(Filters.eq("user", userDocument.getString("_id"))).projection(Projections.include("_id", "name"));

        Map<String, String> stringStringMap = new HashMap<>();

        for (Document listDocument : listDocuments) {
            stringStringMap.put(listDocument.getObjectId("_id").toHexString(), listDocument.getString("name"));
        }

        return stringStringMap;
    }

    @Override
    public void deleteList(String user, String listId) throws UnknownUserException, UnknownListException {

        MongoCollection<Document> usersDocument = getUsersCollection();

        Document userDocument = usersDocument.find(Filters.eq("_id", user)).projection(Projections.include("_id")).first();

        if (userDocument == null) {
            throw new UnknownUserException(user);
        }

        MongoCollection<Document> listsCollection = getListsCollection();

        Document listDocument = listsCollection.find(Filters.eq("_id", new ObjectId(listId))).projection(Projections.include("_id")).first();

        if (listDocument == null) {
            throw new UnknownListException(user, listId);
        }

        listsCollection.deleteOne(listDocument);
    }

    @Override
    public void renameList(String user, String listId, String newName) throws UnknownUserException, UnknownListException {

        MongoCollection<Document> usersDocument = getUsersCollection();

        Document userDocument = usersDocument.find(Filters.eq("_id", user)).projection(Projections.include("_id")).first();

        if (userDocument == null) {
            throw new UnknownUserException(user);
        }

        MongoCollection<Document> listsCollection = getListsCollection();

        Document update = new Document();
        update.append("name", newName);


        UpdateResult updateResult = listsCollection.updateOne(Filters.eq("_id", new ObjectId(listId)), update);
    }

    @Override
    public List<Map<String, Object>> getTasksOfList(String user, String listId) throws UnknownUserException, UnknownListException {
        return null;
    }

    @Override
    public String createListTask(String user, String listId, String taskName) throws UnknownUserException, UnknownListException {
        return null;
    }

    @Override
    public void renameListTask(String user, String listId, String taskId, String newTaskName) throws UnknownUserException, UnknownListException {

    }

    @Override
    public void setListTaskDone(String user, String listId, String taskId, boolean done) throws UnknownUserException, UnknownListException {

    }

    @Override
    public void deleteListTask(String user, String listId, String taskId) throws UnknownUserException, UnknownListException {

    }

    @Override
    public void close() throws Exception {
        mongoClient.close();
    }
}
