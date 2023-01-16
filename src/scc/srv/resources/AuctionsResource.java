package scc.srv.resources;

import scc.cache.RedisCache;
import scc.srv.dataclasses.Bid;
import scc.srv.MainApplication;
import scc.cosmosdb.CosmosDBLayer;
import scc.srv.dataclasses.Auction;
import scc.cosmosdb.models.AuctionDAO;
import scc.srv.dataclasses.AuctionStatus;
import scc.cosmosdb.models.RecentAuctionDAO;
import scc.cosmosdb.models.PopularAuctionDAO;

import java.util.Set;
import java.util.List;
import jakarta.ws.rs.*;
import java.util.Iterator;
import java.util.ArrayList;
import java.lang.reflect.Field;
import jakarta.ws.rs.core.Cookie;
import redis.clients.jedis.Jedis;
import jakarta.ws.rs.core.MediaType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Resource for managing auction.
 */
@Path("/auction")
public class AuctionsResource {

    private MediaResource media;
    private UsersResource users;
    private static Jedis jedis_instance;
    private static CosmosDBLayer db_instance;
    private ObjectMapper mapper;

    private static final String AUCTION_NULL = "Null auction exception";
    private static final String USER_NOT_EXIST = "User does not exist";
    private static final String IMG_NOT_EXIST = "Image does not exist";
    private static final String INVALID_STATUS = "Invalid auction status";
    private static final String NULL_FIELD_EXCEPTION = "Null %s exception";
    private static final String AUCTION_NOT_EXIST = "Auction does not exist";
    private static final String AUCTION_ALREADY_EXISTS = "AuctionId already exists";
    private static final String NEGATIVE_MINPRICE = "minPrice can not be negative or zero";

    public AuctionsResource() {
        db_instance = CosmosDBLayer.getInstance();
        jedis_instance = RedisCache.getCachePool().getResource();
        for (Object resource : MainApplication.getSingletonsSet()) {
            if (resource instanceof MediaResource)
                media = (MediaResource) resource;
            if (resource instanceof UsersResource)
                users = (UsersResource) resource;
        }
        mapper = new ObjectMapper();
    }

    /**
     * 
     * @throws IllegalAccessException
     * @throws IllegalArgumentException
     * @throws JsonProcessingException
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Auction create(@CookieParam("scc:session") Cookie session, Auction auction)
            throws IllegalArgumentException, IllegalAccessException {

        // Winning bids start by default with the value of null
        try {
            users.checkCookieUser(session, auction.getOwnerId());
        } catch (Exception e) {
            // TODO Auto-generated catch block
        }

        String result = checkAuction(auction);

        if (result != null)
            return null;

        String res = jedis_instance.get("auction:" + auction.getId());
        if (res != null)
            return null;

        // Status special verification when creating an auction
        if (!auction.getStatus().equals(AuctionStatus.OPEN.getStatus()))
            result = INVALID_STATUS;

        AuctionDAO dbAuction = new AuctionDAO(auction);
        db_instance.putAuction(dbAuction);
        return auction;
    }

    /**
     * Updates an auction. Throw an appropriate error message if
     * id does not exist.
     * 
     * @throws IllegalAccessException
     * @throws IllegalArgumentException
     */
    @PUT
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Auction update(@CookieParam("scc:session") Cookie session, Auction auction)
            throws IllegalArgumentException, IllegalAccessException {

        try {
            users.checkCookieUser(session, auction.getOwnerId());
        } catch (Exception e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }

        String error = checkAuction(auction);

        if (error != null)
            return null;

        // Actual status
        String currentStatus = getStatusAuction(auction.getId());
        int currentStatusValue = getStatusValue(currentStatus); // Value

        // New Status
        String newStatus = auction.getStatus(); // or dbAuction --> works as well
        int newStatusValue = getStatusValue(newStatus); // Value

        // Status special verification when updating an auction
        if (!isValidStatus(auction.getStatus()) || currentStatusValue > newStatusValue)
            return null;

        // Checks if auctionId exists
        if (!db_instance.getAuctionById(auction.getId()).iterator().hasNext())
            return null;
        AuctionDAO dbAuction = new AuctionDAO(auction);
        db_instance.updateAuction(dbAuction);
        return auction;
    }

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Auction getAuction(@PathParam("id") String id) throws JsonMappingException, JsonProcessingException {
        String res = jedis_instance.get("auction:" + id);
        if (res != null)
            return mapper.readValue(res, Auction.class);
        Iterator<AuctionDAO> it = db_instance.getAuctionById(id).iterator();
        if (it.hasNext())
            return ((((AuctionDAO) it.next()).toAuction()));
        return null;
    }

    /**
     * The most popular auctions are those which have more bids submitted to it
     * The auctions are ordered by number of bids submitted
     * 
     * @param id
     * @return
     */
    @Path("/any/popular")
    @GET
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> popularAuctionsList() {

        List<String> list = new ArrayList<>();

        Iterator<PopularAuctionDAO> it = db_instance.getPopularAuctions().iterator();
        if (it.hasNext())
            list.add(((PopularAuctionDAO) it.next()).toPopularAuction().toString());

        return list;
    }

    @Path("/any/recent")
    @GET
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> openAuctionsUserList() {

        List<String> list = new ArrayList<>();

        Iterator<RecentAuctionDAO> it = db_instance.getRecentAuctions().iterator();
        if (it.hasNext())
            list.add(((RecentAuctionDAO) it.next()).toRecentAuction().toString());

        return list;
    }

    @Path("/any/aboutclose")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> auctionsAboutToClose() {

        List<String> list = new ArrayList<>();

        Set<String> auctions = jedis_instance.keys("auction:*");

        Iterator<String> it = auctions.iterator();
        while (it.hasNext())
            list.add(it.next());

        return list;
    }

    // PRIVATE METHODS

    private boolean isValidStatus(String status) {
        return ((status.equals(AuctionStatus.OPEN.getStatus()) ||
                status.equals(AuctionStatus.CLOSE.getStatus()) ||
                status.equals(AuctionStatus.DELETED.getStatus())) ? true : false);
    }

    private int getStatusValue(String status) {

        for (AuctionStatus auctionStatus : AuctionStatus.values())
            if (status.equals(auctionStatus.getStatus()))
                return auctionStatus.getValue();

        return -1;
    }

    public Bid getAuctionWinningBid(String id) {
        Iterator<AuctionDAO> it = db_instance.getAuctionById(id).iterator();
        if (it.hasNext())
            return ((((AuctionDAO) it.next()).toAuction().getWinningBid()));
        return null;
    }

    private String getStatusAuction(String id) {
        Iterator<AuctionDAO> it = db_instance.getAuctionById(id).iterator();
        if (it.hasNext())
            return ((((AuctionDAO) it.next()).toAuction().getStatus()));
        return null;
    }

    public Auction getAuctionById(String id) {
        Iterator<AuctionDAO> it = db_instance.getAuctionById(id).iterator();
        if (it.hasNext())
            return ((((AuctionDAO) it.next()).toAuction()));
        return null;
    }

    private String checkAuction(Auction auction) throws IllegalArgumentException, IllegalAccessException {

        if (auction == null)
            return AUCTION_NULL;

        // verify that fields are different from null
        for (Field f : auction.getClass().getDeclaredFields()) {
            f.setAccessible(true);
            if (f.get(auction) == null && !f.getName().matches("description|winningBid"))
                return String.format(NULL_FIELD_EXCEPTION, f.getName());
        }

        if (auction.getMinPrice() <= 0)
            return NEGATIVE_MINPRICE;

        if (!db_instance.getUserById(auction.getOwnerId()).iterator().hasNext())
            return USER_NOT_EXIST;

        // verify if imgId exists in the database
        if (!media.verifyImgId(auction.getImgId()))
            return IMG_NOT_EXIST;

        return null;
    }
}