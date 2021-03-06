package eu.europeana.wikidata.connection;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;

import eu.europeana.api.client.connection.HttpConnector;



public class WikidataApiClient {

	private static final Log log = LogFactory.getLog(WikidataApiClient.class);

	private String apiKey;
	public static final String DEFAULT_WIKIDATA_SEARCH_URI = "https://wdq.wmflabs.org/api?";

	private String wikidataApiUri = "";
	private HttpConnector http = new HttpConnector();

	public final static String SEARCH_RESULTS_FOLDER = "./src/test/resources/search/results";

	
	/**
	 * Create a new connection to the Wikidata API.
	 * 
	 * @param apiKey
	 *            API Key provided by Wikidata to access the API
	 */
	public WikidataApiClient(String apiUri, String apiKey) {
		this.apiKey = apiKey;
//		if(apiKey != null)
			this.setApiUri(apiUri);
//		else 
//			setApiKey(getDefaultApiKey());
	}

	public WikidataApiClient() {
		this(DEFAULT_WIKIDATA_SEARCH_URI, null);
		setApiKey(getDefaultApiKey());

	}


	String getJSONResult(String url) throws IOException {
		log.trace("Call to Europeana API: " + url);
		return http.getURLContent(url);

	}

	/**
	 * Returns the Wikidata API URI for JSON calls
	 * e.g. https://wdq.wmflabs.org/api?q=string[646:%27/m/0557q%27]
	 * @param searchString
	 * @return search URL
	 * @throws UnsupportedEncodingException 
	 */
	public String getWikidataSearchUrl(String searchString) throws UnsupportedEncodingException {
		String searchUrl = getApiUri();
//		searchUrl += "q=string[646:%27" + URLEncoder.encode(searchString, "UTF-8") + "%27]";
//		searchUrl += URLEncoder.encode("q=string[646:'" + searchString + "']", "UTF-8");
		searchUrl += "q=" + URLEncoder.encode("string[646:'" + searchString + "']", "UTF-8");
		return searchUrl;
	}

	
	/**
	 * Modifies the Wikidata API URI for JSON calls. The default value points
	 * to the "https://wdq.wmflabs.org/api?"
	 * 
	 * @param apiUri
	 */
	public void setApiUri(String apiUri) {
		this.wikidataApiUri = apiUri;
	}

	
	public String getApiUri() {
		return wikidataApiUri;
	}


	/**
	 * @return the Europeana apiKey
	 */
	public String getApiKey() {
		return apiKey;
	}

	/**
	 * @return the Europeana apiKey
	 */
	public String getDefaultApiKey() {
		return WikidataClientConfiguration.getInstance().getApiKey();
	}
	
	/**
	 * @param apiKey
	 *            the Europeana apiKey to set
	 */
	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}


	/**
	 * To indent JSON string, bind it as Object and write it output with indentation.
	 * @param input The input JSON string
	 * @return indented JSON string
	 */
	public String convertJsonStringToPrettyPrintJsonOutput(String input) {

		String res = input;
		try {
			ObjectMapper mapper = new ObjectMapper();
			Object json = mapper.readValue(input, Object.class);
			res = mapper.defaultPrettyPrintingWriter().writeValueAsString(json);
		} catch (JsonParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JsonMappingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return res;
	}
	
	
	/**
	 * This method stores Wikidata JSON data for related Freebase ID (query) 
	 * in JSON files for given fileName.
	 * @param query
	 * @param folder
	 * @param fileName
	 * @throws IOException
	 */
	public void saveSearchResults(String query, String folder, String fileName)
			throws IOException {
		
		if (fileName.startsWith("m.")) {
			File queryResultsFile = new File(folder, fileName + ".json");
			// create parent dirs
			queryResultsFile.getParentFile().mkdirs();
			BufferedWriter writer = null;
			try {
				writer = new BufferedWriter(new FileWriter(queryResultsFile));
				String searchUrl = getWikidataSearchUrl(query);
				String searchResult = getJSONResult(searchUrl);
				String indentedSearchResult = convertJsonStringToPrettyPrintJsonOutput(searchResult);
				writer.write(indentedSearchResult);
			} finally {
				try {
					writer.close();
				} catch (IOException e) {
					log.warn("cannot close results writer for file: "
							+ queryResultsFile);
				}
			}
		}
	}


	public String queryWikidata(String query)
			throws IOException {
		
		String searchUrl = getWikidataSearchUrl(query);
		String searchResult = getJSONResult(searchUrl);
		return convertJsonStringToPrettyPrintJsonOutput(searchResult);
	}
	
	
	public String queryWikidataIdFromDump(String query, String dumpFile)
			throws IOException {
		
		String res = "";
		
		if (query.startsWith("/"))
			query = query.substring(1);
		query = query.replace("/", ".");
		
		int FREEBASE_POS = 0;
		int WIKIDATA_POS = 2;
    	String splitBy = "\t";	    
	    BufferedReader br;
		try {
			br = new BufferedReader(new FileReader(dumpFile));
			String line = br.readLine();			
			while ((line = br.readLine()) !=null) {
			    String[] b = line.split(splitBy);
			    if (b[FREEBASE_POS].contains(query)) {
			    	String[] wikidata_path = b[WIKIDATA_POS].split("/");
			    	res = wikidata_path[wikidata_path.length - 1].substring(1).replace("> .", ""); // to remove starting 'Q'
			    	break;
			    }
			}
		    br.close();
		} catch (FileNotFoundException e1) {
			log.error("File not found. " + e1.getMessage());
			e1.printStackTrace();
		} catch (IOException e) {
			log.error("IO error. " + e.getMessage());
			e.printStackTrace();
		}
		
		return res;
	}
	
	
    public String getHtmlContent(String query) throws IOException {

    	HttpConnector httpConnection = new HttpConnector();
    	String url = "https://www.wikidata.org/wiki/Q" + query;
    	return httpConnection.getURLContent(url);
    }
	


	public String getSearchResultFromFile(String query) throws IOException {
		String localFolder = SEARCH_RESULTS_FOLDER;
		return readJsonResultFromFile(query, localFolder);

	}

	private String readJsonResultFromFile(String filename, String localFolder)
			throws FileNotFoundException, IOException {
		File queryResultsFile = new File(localFolder, filename + ".json");
		return readJsonFile(queryResultsFile);
	}


	private void savetoJsonFile(File queryResultsFile, String url)
			throws IOException {
		BufferedWriter writer = null;
		try {
			writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(queryResultsFile), "UTF-8"));
					//new FileWriter(queryResultsFile));
			writer.write(getJSONResult(url));
		} finally {
			try {
				writer.close();
			} catch (IOException e) {
				log.warn("cannot close results writer for file: "
						+ queryResultsFile);
			}
		}
	}

	private String readJsonFile(File queryResultsFile)
			throws FileNotFoundException, IOException {
		BufferedReader reader = null;
		StringBuilder builder = new StringBuilder();
		String line;
		try {
			reader = new BufferedReader(new InputStreamReader(new FileInputStream(queryResultsFile), "UTF-8"));
			// new FileReader(queryResultsFile));
			while ((line = reader.readLine()) != null)
				builder.append(line);

		} finally {
			try {
				if (reader != null)
					reader.close();
			} catch (IOException e) {
				log.warn("cannot close results writer for file: "
						+ queryResultsFile);
			}
		}

		return builder.toString();
	}


	private File getSearchResultsFile(String filename) {
		File queryResultsFile = new File(SEARCH_RESULTS_FOLDER, filename
				+ ".json");
		return queryResultsFile;
	}
}
