package scc.cosmosdb;

import java.util.Iterator;

import com.azure.cosmos.ConsistencyLevel;
import com.azure.cosmos.CosmosClient;
import com.azure.cosmos.CosmosClientBuilder;
import com.azure.cosmos.CosmosContainer;
import com.azure.cosmos.CosmosDatabase;
import com.azure.cosmos.models.CosmosItemRequestOptions;
import com.azure.cosmos.models.CosmosItemResponse;
import com.azure.cosmos.models.CosmosQueryRequestOptions;
import com.azure.cosmos.models.PartitionKey;
import com.azure.cosmos.util.CosmosPagedIterable;

import scc.cosmosdb.models.AuctionDAO;
import scc.cosmosdb.models.BidDAO;
import scc.cosmosdb.models.LoginDAO;
import scc.cosmosdb.models.PopularAuctionDAO;
import scc.cosmosdb.models.QuestionDAO;
import scc.cosmosdb.models.RecentAuctionDAO;
import scc.cosmosdb.models.UserDAO;
import scc.srv.dataclasses.AuctionStatus;

public class CosmosDBLayer {
	// private static final String CONNECTION_URL =
	// "https://tiagoduarte25.documents.azure.com:443/";
	// private static final String DB_KEY =
	// "2OaqfBxw7Yrc1cKC6DK7SMmhiUBhF7wnFWppftLgFWMfZROg5iyYuRxI0LUsCXyhcas7et2Rrb9sACDbNBTB9w==";
	// private static final String DB_NAME = "ToDoList";

	private static final String CONNECTION_URL = "https://scc22235.documents.azure.com:443/";
	private static final String DB_KEY = "LTtd6QsAm0SN4X56YVCvoYGPquvr1KAuPi5UFoiDD6g0oDgTvrTpH56T4R5qo046xm8UYzbNwFtFePvJuTdNJg==";
	private static final String DB_NAME = "container";

	private static CosmosDBLayer instance;

	public static synchronized CosmosDBLayer getInstance() {
		if (instance != null)
			return instance;

		CosmosClient client = new CosmosClientBuilder()
				.endpoint(CONNECTION_URL)
				.key(DB_KEY)
				// .directMode()
				.gatewayMode()
				// replace by .directMode() for better performance
				.consistencyLevel(ConsistencyLevel.SESSION)
				.connectionSharingAcrossClientsEnabled(true)
				.contentResponseOnWriteEnabled(true)
				.buildClient();
		instance = new CosmosDBLayer(client);
		return instance;

	}

	private CosmosDatabase db;
	private CosmosClient client;
	private CosmosContainer bids;
	private CosmosContainer login;
	private CosmosContainer users;
	private CosmosContainer auctions;
	private CosmosContainer questions;
	private CosmosContainer popularAuctions;
	private CosmosContainer recentAuctions;

	public CosmosDBLayer(CosmosClient client) {
		this.client = client;
	}

	private synchronized void init() {
		if (db != null)
			return;
		db = client.getDatabase(DB_NAME);
		users = db.getContainer("users");
		auctions = db.getContainer("auctions");
		questions = db.getContainer("questions");
		bids = db.getContainer("bids");
		login = db.getContainer("login");
		popularAuctions = db.getContainer("popularAuctions");
		recentAuctions = db.getContainer("recentAuctions");

	}

	public CosmosItemResponse<Object> delUserById(String id) {
		init();
		PartitionKey key = new PartitionKey(id);
		return users.deleteItem(id, key, new CosmosItemRequestOptions());
	}

	public CosmosItemResponse<Object> delUser(UserDAO user) {
		init();
		return users.deleteItem(user, new CosmosItemRequestOptions());
	}

	public CosmosItemResponse<UserDAO> putUser(UserDAO user) {
		init();
		return users.createItem(user);
	}

	public CosmosItemResponse<QuestionDAO> putQuestion(QuestionDAO question) {
		init();
		return questions.createItem(question);
	}

	public CosmosItemResponse<UserDAO> updateUser(UserDAO user) {
		init();
		return users.upsertItem(user);
	}

	public CosmosPagedIterable<UserDAO> getUserById(String id) {
		init();
		return users.queryItems("SELECT * FROM users WHERE users.id=\"" + id + "\"", new CosmosQueryRequestOptions(),
				UserDAO.class);
	}

	public CosmosPagedIterable<UserDAO> getUsers() {
		init();
		return users.queryItems("SELECT * FROM users ", new CosmosQueryRequestOptions(), UserDAO.class);
	}

	public CosmosPagedIterable<AuctionDAO> getAuctionsByUserId(String id) {
		init();
		return auctions.queryItems("SELECT * FROM auctions WHERE auctions.ownerId=\"" + id + "\"",
				new CosmosQueryRequestOptions(), AuctionDAO.class);
	}

	public void close() {
		client.close();
	}

	public CosmosItemResponse<AuctionDAO> putAuction(AuctionDAO auction) {
		init();
		return auctions.createItem(auction);
	}

	public CosmosItemResponse<BidDAO> putBid(BidDAO bid) {
		init();
		return bids.createItem(bid);
	}

	public CosmosPagedIterable<BidDAO> getBidById(String id) {
		init();
		return bids.queryItems("SELECT * FROM bids WHERE bids.id=\"" + id + "\"",
				new CosmosQueryRequestOptions(),
				BidDAO.class);
	}

	public CosmosPagedIterable<BidDAO> getBidsByAuctionId(String id) {
		init();
		return bids.queryItems("SELECT * FROM bids WHERE bids.auctionId=\"" + id + "\" ORDER BY bids.amount DESC",
				new CosmosQueryRequestOptions(),
				BidDAO.class);
	}

	public CosmosPagedIterable<QuestionDAO> getQuestionsById(String id) {
		init();
		return questions.queryItems("SELECT * FROM questions WHERE questions.id=\"" + id + "\"",
				new CosmosQueryRequestOptions(),
				QuestionDAO.class);
	}

	public CosmosPagedIterable<QuestionDAO> getQuestionsByAuctionId(String id) {
		init();
		return questions.queryItems("SELECT * FROM questions WHERE questions.auctionId=\"" + id + "\"",
				new CosmosQueryRequestOptions(),
				QuestionDAO.class);
	}

	public CosmosPagedIterable<AuctionDAO> getAuctionById(String id) {
		init();
		return auctions.queryItems("SELECT * FROM auctions WHERE auctions.id=\"" + id + "\"",
				new CosmosQueryRequestOptions(),
				AuctionDAO.class);
	}

	public CosmosItemResponse<AuctionDAO> updateAuction(AuctionDAO dbAuction) {
		init();
		return auctions.upsertItem(dbAuction);
	}

	public CosmosItemResponse<QuestionDAO> updateQuestion(QuestionDAO dbQuestion) {
		init();
		return questions.upsertItem(dbQuestion);
	}

	public CosmosItemResponse<LoginDAO> putLogin(LoginDAO loginDAO) {
		init();
		return login.createItem(loginDAO);
	}

	public CosmosPagedIterable<LoginDAO> getLoginById(String id) {
		init();
		return login.queryItems("SELECT * FROM login WHERE login.id=\"" + id + "\"",
				new CosmosQueryRequestOptions(),
				LoginDAO.class);
	}

	public CosmosPagedIterable<AuctionDAO> getOpenAuctions(String id) {
		init();
		return auctions.queryItems(
				"SELECT * FROM auctions WHERE auctions.ownerId=\"" + id + "\"" + "AND auctions.status=\""
						+ AuctionStatus.OPEN.getStatus() + "\"",
				new CosmosQueryRequestOptions(),
				AuctionDAO.class);
	}

	public CosmosPagedIterable<AuctionDAO> getAllAuctions() {
		init();
		return auctions.queryItems("SELECT * FROM auctions",
				new CosmosQueryRequestOptions(),
				AuctionDAO.class);
	}

	public CosmosPagedIterable<QuestionDAO> getQuestionsByUserId(String id) {
		init();
		return questions.queryItems("SELECT * FROM questions WHERE questions.userId=\"" + id + "\"",
				new CosmosQueryRequestOptions(),
				QuestionDAO.class);
	}

	public CosmosPagedIterable<BidDAO> getBidsByUserId(String id) {
		init();
		return bids.queryItems("SELECT * FROM bids WHERE bids.userId=\"" + id + "\"",
				new CosmosQueryRequestOptions(),
				BidDAO.class);
	}

	public CosmosPagedIterable<AuctionDAO> getAuctionUserFollow(String id) {
		init();
		return auctions.queryItems("SELECT * FROM auctions WHERE auctions.winningBid.userId=\"" + id + "\"",
				new CosmosQueryRequestOptions(),
				AuctionDAO.class);
	}

	public CosmosPagedIterable<PopularAuctionDAO> getPopularAuctions() {
		init();
		return popularAuctions.queryItems("SELECT * FROM popularAuctions", new CosmosQueryRequestOptions(),
				PopularAuctionDAO.class);
	}

	public CosmosPagedIterable<RecentAuctionDAO> getRecentAuctions() {
		init();
		return recentAuctions.queryItems("SELECT * FROM recentAuctions", new CosmosQueryRequestOptions(),
				RecentAuctionDAO.class);
	}

	public void closeAuctions() {
		init();
		CosmosPagedIterable<AuctionDAO> cpi = auctions.queryItems(
				"SELECT * FROM auctions WHERE auctions.status=\"" + AuctionStatus.OPEN.getStatus() + "\""
						+ "AND auctions.endTime <= GetCurrentTimestamp()",
				new CosmosQueryRequestOptions(),
				AuctionDAO.class);
		Iterator<AuctionDAO> it = cpi.iterator();
		while (it.hasNext()) {
			AuctionDAO auction = it.next();
			auction.setStatus(AuctionStatus.CLOSE.getStatus());
			auctions.upsertItem(auction);
		}
	}

	public CosmosPagedIterable<AuctionDAO> getAuctionsAboutToClose() {
		init();
		return auctions.queryItems(
				"SELECT * FROM auctions WHERE auctions.status=\"" + AuctionStatus.OPEN.getStatus() + "\""
						+ "AND auctions.endTime <= (GetCurrentTimestamp() + 86400)",
				new CosmosQueryRequestOptions(),
				AuctionDAO.class);
	}
}
