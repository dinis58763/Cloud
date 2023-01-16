package scc.srv.resources;

import scc.cache.RedisCache;
import scc.srv.MainApplication;
import scc.cosmosdb.CosmosDBLayer;
import scc.cosmosdb.models.UserDAO;
import scc.srv.dataclasses.Auction;
import scc.srv.dataclasses.Question;
import scc.cosmosdb.models.AuctionDAO;
import scc.cosmosdb.models.QuestionDAO;

import java.util.List;
import jakarta.ws.rs.*;
import java.util.Iterator;
import java.util.ArrayList;
import java.lang.reflect.Field;
import jakarta.ws.rs.core.Cookie;
import redis.clients.jedis.Jedis;
import jakarta.ws.rs.core.MediaType;
import com.azure.cosmos.util.CosmosPagedIterable;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * Resource for managing questions.
 */
@Path("/auction/{id}/question")
public class QuestionsResource {

    private static final int DEFAULT_REDIS_EXPIRE = 600;
    private static final String AUCTION_ERROR = "Auction does not exist";
    private static final String NULL_FIELD_EXCEPTION = "Null %s exception";
    private static final String USER_NOT_EXISTS = "Error non-existent user";
    private static final String QUESTION_NULL = "Error creating null question";
    private static final String AUCTION_NOT_EXISTS = "Error non-existent auction";
    private static final String ONLY_OWNER_ERROR = "Only owner can reply to questions";
    private static final String ALREADY_EXISTS_DB = "Id already exists in the DataBase";
    private static final String SAME_OWNER = "Owner can not ask a question in his auction";
    private static final String REPLY_ALREADY_DONE = "Only one reply can be made for a question";
    private static final String AUCTION_ID_NOT_EXISTS_DB = "Auction does not exist in the DataBase";

    private ObjectMapper mapper;
    private UsersResource users;
    private static Jedis jedis_instance;
    private static CosmosDBLayer db_instance;

    public QuestionsResource() {
        db_instance = CosmosDBLayer.getInstance();
        jedis_instance = RedisCache.getCachePool().getResource();
        mapper = new ObjectMapper();
        for (Object resource : MainApplication.getSingletonsSet()) {
            if (resource instanceof UsersResource)
                users = (UsersResource) resource;
        }
    }

    /**
     * Creates a new question.
     * 
     * @throws Exception
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Question create(@CookieParam("scc:session") Cookie session, Question question,
            @PathParam("id") String auctionId)
            throws Exception {

        // try {
        // users.checkCookieUser(session, question.getUserId());
        // } catch (Exception e) {
        // // TODO Auto-generated catch block
        // throw new Exception("COOKIE QUESTION");
        // }

        checkQuestion(question);

        // if (getAuctionOwner(auctionId).equals(question.getUserId()))
        // return null;

        // Create the question to store in the db
        QuestionDAO dbquestion = new QuestionDAO(question);
        jedis_instance.setex("question:" + question.getId(), DEFAULT_REDIS_EXPIRE, mapper.writeValueAsString(question));

        db_instance.putQuestion(dbquestion);
        return question;
    }

    @GET
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> listAuctionQuestions(@PathParam("id") String auctionId)
            throws IllegalArgumentException, IllegalAccessException {

        List<String> list = new ArrayList<>();

        if (!auctionExistsInDB(auctionId)) {
            list.add(AUCTION_ID_NOT_EXISTS_DB);
            return list;
        }

        Iterator<QuestionDAO> it = db_instance.getQuestionsByAuctionId(auctionId).iterator();

        if (it.hasNext())
            list.add(((QuestionDAO) it.next()).toQuestion().toString());

        return list;
    }

    @Path("/{id}")
    @GET
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Question listAuctionQuestion(@PathParam("id") String auctionId,
            @PathParam("id") String questionId) throws IllegalArgumentException, IllegalAccessException {

        if (!auctionExistsInDB(auctionId))
            return null;

        if (!questionExistsInDB(questionId))
            return null;

        return getQuestionById(questionId);
    }

    /**
     * Reply to a question.
     * 
     * @throws Exception
     */
    @Path("/{id}/reply")
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Question reply(@CookieParam("scc:session") Cookie session, Question question,
            @PathParam("id") String auctionId, @PathParam("id") String questionId)
            throws Exception {

        try {
            users.checkCookieUser(null, question.getUserId());
        } catch (Exception e) {
            // TODO Auto-generated catch block
            throw new Exception("COOKIE REPLY QUESTION");
        }

        checkQuestion(question);

        if (!question.getUserId().equals(getAuctionOwner(auctionId)))
            return null;

        Question questioned = getQuestionById(questionId);

        String reply = questioned.getReply();
        String newReply = question.getMessage();

        if (reply != null)
            return null;

        // Updates the question in redis with the new reply and stores the new question
        // (reply)
        try {
            String res = jedis_instance.get("question:" + questioned.getId());
            if (res != null) {
                questioned.setReply(newReply);
                jedis_instance.setex("question:" + questioned.getId(), DEFAULT_REDIS_EXPIRE,
                        mapper.writeValueAsString(questioned));
                jedis_instance.setex("question:" + question.getId(), DEFAULT_REDIS_EXPIRE,
                        mapper.writeValueAsString(question));
            }
        } catch (Exception e) {
            // TODO: handle exception
        }

        // Updates the question in the database with the new reply and stores the new
        // question (reply)
        QuestionDAO dbQuestion = new QuestionDAO(questioned);
        dbQuestion.setReply(newReply);
        db_instance.updateQuestion(dbQuestion);

        // QuestionDAO dbReply = new QuestionDAO(question);
        // db_instance.putQuestion(dbReply);
        return dbQuestion.toQuestion();
    }

    // PRIVATE METHODS

    private String getAuctionOwner(String auctionId) throws JsonMappingException, JsonProcessingException {
        String auction_res = jedis_instance.get("auction:" + auctionId);
        if (auction_res != null) {
            Auction auction = mapper.readValue(auction_res, Auction.class);
            return auction.getOwnerId();
        }

        CosmosPagedIterable<AuctionDAO> auctionsIt = db_instance.getAuctionById(auctionId);
        if (!auctionsIt.iterator().hasNext())
            return AUCTION_ERROR; // this should never happen.
        AuctionDAO auc = auctionsIt.iterator().next();
        return auc.getOwnerId();
    }

    private Question getQuestionById(String id) {
        Iterator<QuestionDAO> auctionsIt = db_instance.getQuestionsById(id).iterator();
        while (auctionsIt.hasNext())
            return auctionsIt.next().toQuestion();
        return null;
    }

    private boolean questionExistsInDB(String questionId) {
        CosmosPagedIterable<QuestionDAO> questionsIt = db_instance.getQuestionsById(questionId);
        return questionsIt.iterator().hasNext();
    }

    private boolean userExistsInDB(String userId) {
        CosmosPagedIterable<UserDAO> usersIt = db_instance.getUserById(userId);
        return usersIt.iterator().hasNext();
    }

    private boolean auctionExistsInDB(String auctionId) {
        CosmosPagedIterable<AuctionDAO> auctionIt = db_instance.getAuctionById(auctionId);
        return auctionIt.iterator().hasNext();
    }

    private void checkQuestion(Question question) throws Exception {

        if (question == null)
            throw new Exception("Question null");

        if (questionExistsInDB(question.getId()))
            throw new Exception("Question exists in db");

        // verify that fields are different from null
        for (Field f : question.getClass().getDeclaredFields()) {
            f.setAccessible(true);
            if (f.get(question) == null && !f.getName().equals("reply"))
                throw new Exception(String.format(NULL_FIELD_EXCEPTION, f.getName()));
        }

        if (!userExistsInDB(question.getUserId()))
            throw new Exception("User not exists in db");

        // this does not make sense we're only doing this for the moment
        if (!auctionExistsInDB(question.getAuctionId()))
            throw new Exception("AUCTION not exists in db");
    }
}
