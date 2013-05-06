package core;

import java.util.List;

import org.json.JSONObject;

/**
 * Represent a bot.
 * @author Paul Chaignon
 */
public class Bot extends Resource {
	private List<BotSuggestion> moves;
	
	/**
	 * Constructor
	 * @param uri The URI.
	 * @param name The name.
	 * @param trust The trust in this bot.
	 */
	public Bot(String uri, String name, int trust) {
		super(uri, name, trust);
	}

	@Override
	public List<BotSuggestion> getMoveSuggestions() {
		return this.moves;
	}

	@Override
	protected void clearSuggestions() {
		this.moves.clear();
	}

	@Override
	protected void parseJSONMove(String response, String fen) {
		JSONObject json = new JSONObject(response);
		String move = json.getString(JSON_MOVE);
		BotSuggestion suggestion = new BotSuggestion(move);
		this.moves.add(suggestion);
	}
}