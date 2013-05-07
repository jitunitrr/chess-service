package core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Manage the list of resources.
 * @author Benoit Travers
 * @author Paul Chaignon
 * @author Clement Gautrais
 */
public class CentralServer {
	private Set<Resource> resources;
	private static final String version = "1.0";
	private static final int REWARD_VALUE = 20;

	/**
	 * Constructor
	 */
	public CentralServer() {
		this.resources = new HashSet<Resource>();
		this.restoreResources();
		this.checkVersion();
	}

	/**
	 * Restore the list of resources from the database file.
	 */
	private void restoreResources() {
		this.resources = ResourcesManager.getResources(true);
	}

	/**
	 * Check resources version.
	 */
	private void checkVersion() {
		String centralserveur_version = CentralServer.version.substring(0, CentralServer.version.indexOf('.'));
		Set<Resource> incompatibleResources = new HashSet<Resource>();

		for(Resource resource: this.resources) {
			resource.checkVersion();


			if(!resource.isConnected()) {
				incompatibleResources.add(resource);
			} else {
				String resource_version = resource.getVersion().substring(0, resource.getVersion().indexOf("."));
				if(!centralserveur_version.equals(resource_version)) {
					incompatibleResources.add(resource);
				}
			}
		}

		// TODO Save incompatible resources.

		for(Resource resource: incompatibleResources) {
			this.resources.remove(resource);
		}
	}

	/**
	 * save a resource in the corresponding table
	 */
	public void saveResourcesTrust(Set<Resource> resources) {
		ResourcesManager.updateResourcesTrust(resources);	
	}

	/**
	 * Get the suggestion of move from the resources and compute the best answer.
	 * @param fen The FEN.
	 * @param gameId The game id.
	 * @return The best move or null if no suggestion.
	 */
	public String getBestMove(String fen, int gameId) {
		this.updateResources(fen);
		// This map contains all the moves and the scores associated except the ending moves.
		Map<String, Double> scores = new HashMap<String, Double>();
		// This map contains the ending moves with their scores.
		Map<String, Double> ends = new HashMap<String, Double>();

		for(Resource resource : this.resources) {
			for(MoveSuggestion move : resource.getMoveSuggestions()) {
				if(move.getClass()==EndingSuggestion.class) {
					ends.put(move.getMove(), move.getScore());	
				} else {
					if(scores.containsKey(move.getMove())) {
						double newScore = scores.get(move) + resource.getTrust()*move.getScore();
						scores.put(move.getMove(), newScore);
					} else {
						scores.put(move.getMove(), resource.getTrust()*move.getScore());
					}
				}
			}
		}
		
		String bestMove = this.bestMove(scores, ends);
		
		if(gameId>0) {
			GamesManager.addMoves(gameId, getMoveResourcesConfidence(bestMove), GamesManager.getNumberOfMoves(gameId)+1);
		}
		
		StatsManager.updateStatistics(getSuggestions());

		if(bestMove==null) {
			return null;
		}
		System.out.println(bestMove); // Call listener instead.
		return bestMove;
	}

	/**
	 * Get the best move by comparing the scores among all moves suggested.
	 * @param moves The map containing all the moves and the scores associated.
	 * @param ends The map containing all the ending moves and their scores.
	 * @return The best move among all moves or null if no suggestion.
	 */
	private String bestMove(Map<String, Double> moves, Map<String, Double> ends) {
		String move = null;
		if(ends.size()>0) {
			double min = Double.MAX_VALUE;
			for(Map.Entry<String, Double> entry: ends.entrySet()) {
				if(entry.getValue() < min) {
					min = entry.getValue();
					move = entry.getKey();
				}
			}
			if(min > 0) {
				// Use an end suggestion only if we'll win.
				return move;
			}
		}
		double max = Double.MIN_VALUE;
		move = null;
		for(Map.Entry<String, Double> entry: moves.entrySet()) {
			if(entry.getValue() > max) {
				max = entry.getValue();
				move = entry.getKey();
			}
		}
		return move;
	}
	
	/**
	 * Return all the resources proposing the move move
	 * @param move the move that is proposed
	 * @return resources
	 */
	private Map<Integer, Double> getMoveResourcesConfidence(String move){
		Set<Resource> moveResources = new HashSet<Resource>();
		Map<Integer, Double> resourcesConfidence = new HashMap<Integer, Double>();
		double scoreMax = Double.MIN_VALUE;
		for(Resource r : resources){
			for(MoveSuggestion moveSug : r.getMoveSuggestions()){
				if(moveSug.getMove().equals(move)){
					if(moveSug.getScore() > scoreMax)
						scoreMax = moveSug.getScore();
					moveResources.add(r);
				}
			}
		}
		
		for(Resource r : moveResources){
			for(MoveSuggestion moveSug : r.getMoveSuggestions()){
				if(moveSug.getMove().equals(move)){
					if(scoreMax>0)
						resourcesConfidence.put(r.getId(), moveSug.getScore()/scoreMax);
					else if(scoreMax<0)
						resourcesConfidence.put(r.getId(), scoreMax/moveSug.getScore());
					else
						resourcesConfidence.put(r.getId(), (double)0);
				}
			}
		}
		
		return resourcesConfidence;
	}
	
	/**
	 * Return all the Suggestions made by the resources
	 * @return the Suggestions
	 */
	private Set<? extends MoveSuggestion> getSuggestions(){
		HashSet<MoveSuggestion> moves = new HashSet<>();
		for(Resource r : resources){
			for(MoveSuggestion moveSug : r.getMoveSuggestions()){
				moves.add(moveSug);
			}
		}
		return moves;
	}
	
	public void rewardResources(int game_id, int game_result){
		int rewardValue=REWARD_VALUE;
		if(game_result == EndingSuggestion.LOOSE_RESULT)
			rewardValue*=-1;

		if(game_result!=EndingSuggestion.DRAW_RESULT){
			Map<Integer, Double> resourcesStats = GamesManager.getResourcesStats(game_id);

			for(Map.Entry<Integer, Double> entry : resourcesStats.entrySet()){
				Resource r = getResource(entry.getKey());
				if(r!=null){
					r.setTrust(r.getTrust()+(int)(rewardValue*entry.getValue()));
				}
			}
			ResourcesManager.updateResourcesTrust(resources);
		}
	}
	
	private Resource getResource(int id){
		for(Resource r : resources){
			if(r.getId()==id)
				return r;
		}
		return null;
	}

	/**
	 * Ask for all resources to update their suggestions of move.
	 * Do it using multithreading.
	 * Wait for the end of all updates.
	 * @param fen The FEN.
	 */
	private void updateResources(final String fen) {
		Set<Thread> threads = new HashSet<Thread>();
		for(final Resource resource: this.resources) {
			Thread thread = new Thread(new Runnable() {
				@Override
				public void run() {
					resource.query(fen);
				}
			});
			thread.start();
			threads.add(thread);
		}
		for(Thread thread: threads) {
			try {
				thread.join();
			} catch (InterruptedException e) {
				// Shouldn't happen.
				System.err.println("The thread was interrupted: "+e.getMessage());
			}
		}
	}
}