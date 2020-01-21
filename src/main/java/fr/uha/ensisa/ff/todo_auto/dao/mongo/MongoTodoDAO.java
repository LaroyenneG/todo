package fr.uha.ensisa.ff.todo_auto.dao.mongo;

import com.mongodb.MongoWriteException;
import com.mongodb.client.*;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import fr.uha.ensisa.ff.todo_auto.dao.TodoDAO;
import fr.uha.ensisa.ff.todo_auto.dao.UnknownListException;
import fr.uha.ensisa.ff.todo_auto.dao.UnknownUserException;
import fr.uha.ensisa.ff.todo_auto.dao.UserAlreadyExistsException;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MongoTodoDAO implements TodoDAO {

    private static final int DOCUMENT_KEY_DUPLICATION_CODE = 11000;

    private static final String CLUSTER_ADDRESS = "mongodb://localhost:27017";

    private static final String DATABASE_NAME = "todo";

    private static final String USER_COLLECTION_NAME = "users";
    private static final String LIST_COLLECTION_NAME = "lists";

    private static final String ID_FIELD = "_id";
    private static final String PASSWORD_FIELD = "password";
    private static final String TASKS_FIELD = "tasks";
    private static final String NAME_FIELD = "name";
    private static final String DONE_FIELD = "done";
    private static final String USER_FIELD = "user";

    private final MongoClient mongoClient;
    private final MongoCollection<Document> usersCollection;
    private final MongoCollection<Document> listsCollection;
    private final MongoDatabase mongoDatabase;

    public MongoTodoDAO() {
        mongoClient = MongoClients.create(CLUSTER_ADDRESS);
        mongoDatabase = mongoClient.getDatabase(DATABASE_NAME);
        usersCollection = mongoDatabase.getCollection(USER_COLLECTION_NAME);
        listsCollection = mongoDatabase.getCollection(LIST_COLLECTION_NAME);
        listsCollection.createIndex(Indexes.hashed(USER_FIELD));
    }


    @Override
    public void registerUser(String user, String password) throws UserAlreadyExistsException {

        Document userDocument = new Document();
        userDocument.append(ID_FIELD, user);
        userDocument.append(PASSWORD_FIELD, password);

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

        Document userDocument = usersCollection.find(Filters.eq(ID_FIELD, user)).projection(Projections.fields(Projections.excludeId(), Projections.include(PASSWORD_FIELD))).first();

        if (userDocument == null) {
            throw new UnknownUserException(user);
        }

        return userDocument.getString(PASSWORD_FIELD);
    }

    @Override
    public String createDefaultTask(String user, String taskName) throws UnknownUserException {

        Document defaultTaskDocument = new Document();

        defaultTaskDocument.append(ID_FIELD, new ObjectId());
        defaultTaskDocument.append(NAME_FIELD, taskName);
        defaultTaskDocument.append(DONE_FIELD, false);

        UpdateResult updateResult = usersCollection.updateOne(Filters.eq(ID_FIELD, user), Updates.push(TASKS_FIELD, defaultTaskDocument));

        if (!updateResult.wasAcknowledged()) {
            throw new UnknownUserException(user);
        }

        return defaultTaskDocument.getObjectId(ID_FIELD).toHexString();
    }

    @Override
    public List<Map<String, Object>> getDefaultTasks(String user) throws UnknownUserException {

        Document userDocument = usersCollection.find(Filters.eq(ID_FIELD, user)).projection(Projections.fields(Projections.excludeId(), Projections.include(TASKS_FIELD))).first();

        if (userDocument == null) {
            throw new UnknownUserException(user);
        }

        List<Document> defaultTasks = userDocument.getList(TASKS_FIELD, Document.class);

        List<Map<String, Object>> mapList = new ArrayList<>();

        if (defaultTasks != null) {

            for (Document task : defaultTasks) {

                Map<String, Object> stringObjectMap = new HashMap<>();

                stringObjectMap.put("id", task.getObjectId(ID_FIELD).toHexString());
                stringObjectMap.put("name", task.getString(NAME_FIELD));
                stringObjectMap.put("done", task.getBoolean(DONE_FIELD));

                mapList.add(stringObjectMap);
            }

        }

        return mapList;
    }

    @Override
    public void setDefaultTaskDone(String user, String taskId, boolean done) throws UnknownUserException {

        UpdateResult updateResult = usersCollection.updateOne(Filters.and(Filters.eq(ID_FIELD, user), Filters.eq(TASKS_FIELD + '.' + ID_FIELD, new ObjectId(taskId))), Updates.set(TASKS_FIELD + ".$." + DONE_FIELD, done));

        if (!updateResult.wasAcknowledged()) {
            throw new UnknownUserException(user);
        }
    }

    @Override
    public void renameDefaultTask(String user, String taskId, String newName) throws UnknownUserException {

        UpdateResult updateResult = usersCollection.updateOne(Filters.and(Filters.eq(ID_FIELD, user), Filters.eq(TASKS_FIELD + '.' + ID_FIELD, new ObjectId(taskId))), Updates.set(TASKS_FIELD + ".$." + NAME_FIELD, NAME_FIELD));

        if (!updateResult.wasAcknowledged()) {
            throw new UnknownUserException(user);
        }
    }

    @Override
    public void deleteDefaultTask(String user, String taskId) throws UnknownUserException {

        UpdateResult updateResult = usersCollection.updateOne(Filters.eq(ID_FIELD, user), Updates.pull(TASKS_FIELD, Filters.eq(TASKS_FIELD + '.' + ID_FIELD, new ObjectId(taskId))));

        if (!updateResult.wasAcknowledged()) {
            throw new UnknownUserException(user);
        }
    }

    @Override
    public String createList(String user, String name) {

        Document listDocument = new Document();
        listDocument.put(USER_FIELD, user);
        listDocument.put(NAME_FIELD, name);
        listsCollection.insertOne(listDocument);

        return listDocument.getObjectId(ID_FIELD).toHexString();
    }

    @Override
    public Map<String, String> getLists(String user) {

        FindIterable<Document> listDocuments = listsCollection.find(Filters.eq(USER_FIELD, user)).projection(Projections.include(NAME_FIELD));

        Map<String, String> stringStringMap = new HashMap<>();

        for (Document listDocument : listDocuments) {
            stringStringMap.put(listDocument.getObjectId(ID_FIELD).toHexString(), listDocument.getString(NAME_FIELD));
        }

        return stringStringMap;
    }

    @Override
    public void deleteList(String user, String listId) throws UnknownListException {


        DeleteResult deleteResult = listsCollection.deleteOne(Filters.eq(ID_FIELD, new ObjectId(listId)));

        if (!deleteResult.wasAcknowledged()) {
            throw new UnknownListException(user, listId);
        }
    }

    @Override
    public void renameList(String user, String listId, String newName) throws UnknownListException {

        UpdateResult updateResult = listsCollection.updateOne(Filters.eq(ID_FIELD, new ObjectId(listId)), Updates.set(NAME_FIELD, newName));

        if (!updateResult.wasAcknowledged()) {
            throw new UnknownListException(user, listId);
        }
    }

    @Override
    public List<Map<String, Object>> getTasksOfList(String user, String listId) throws UnknownListException {


        Document listDocument = listsCollection.find(Filters.eq(ID_FIELD, new ObjectId(listId))).projection(Projections.include(TASKS_FIELD)).first();

        if (listDocument == null) {
            throw new UnknownListException(user, listId);
        }

        List<Map<String, Object>> mapList = new ArrayList<>();

        List<Document> tasks = listDocument.getList(TASKS_FIELD, Document.class);

        if (tasks != null) {

            for (Document task : tasks) {

                Map<String, Object> stringObjectMap = new HashMap<>();

                stringObjectMap.put("id", task.getObjectId(ID_FIELD).toHexString());
                stringObjectMap.put("name", task.getString(NAME_FIELD));
                stringObjectMap.put("done", task.getBoolean(DONE_FIELD));

                mapList.add(stringObjectMap);
            }
        }

        return mapList;
    }

    @Override
    public String createListTask(String user, String listId, String taskName) throws UnknownListException {


        Document taskDocument = new Document();

        taskDocument.append(ID_FIELD, new ObjectId());
        taskDocument.append(NAME_FIELD, taskName);
        taskDocument.append(DONE_FIELD, false);

        UpdateResult updateResult = listsCollection.updateOne(Filters.eq(ID_FIELD, new ObjectId(listId)), Updates.push(TASKS_FIELD, taskDocument));

        if (!updateResult.wasAcknowledged()) {
            throw new UnknownListException(user, listId);
        }

        return taskDocument.getObjectId(ID_FIELD).toHexString();
    }

    @Override
    public void renameListTask(String user, String listId, String taskId, String newTaskName) throws UnknownListException {


        UpdateResult updateResult = listsCollection.updateOne(Filters.and(Filters.eq(ID_FIELD, new ObjectId(listId)), Filters.eq(TASKS_FIELD + '.' + ID_FIELD, new ObjectId(taskId))), Updates.set(TASKS_FIELD + ".$." + NAME_FIELD, newTaskName));

        if (!updateResult.wasAcknowledged()) {
            throw new UnknownListException(user, listId);
        }
    }

    @Override
    public void setListTaskDone(String user, String listId, String taskId, boolean done) throws UnknownListException {

        UpdateResult updateResult = listsCollection.updateOne(Filters.and(Filters.eq(ID_FIELD, new ObjectId(listId)), Filters.eq(TASKS_FIELD + '.' + ID_FIELD, new ObjectId(taskId))), Updates.set(TASKS_FIELD + ".$." + DONE_FIELD, done));

        if (!updateResult.wasAcknowledged()) {
            throw new UnknownListException(user, listId);
        }
    }

    @Override
    public void deleteListTask(String user, String listId, String taskId) throws UnknownListException {

        UpdateResult updateResult = listsCollection.updateOne(Filters.eq(ID_FIELD, new ObjectId(listId)), Updates.pull(TASKS_FIELD, Filters.eq(TASKS_FIELD + '.' + ID_FIELD, new ObjectId(taskId))));

        if (!updateResult.wasAcknowledged()) {
            throw new UnknownListException(user, listId);
        }
    }

    @Override
    public void close() throws Exception {
        mongoClient.close();
    }
}
