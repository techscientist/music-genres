package eu.europeana.search.connection;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import eu.europeana.api.client.EuropeanaApi2Client;
import eu.europeana.api.client.exception.EuropeanaApiProblem;
import eu.europeana.api.client.metadata.MetadataAccessor;
import eu.europeana.api.client.model.search.CommonMetadata;
import eu.europeana.api.client.search.query.Api2QueryBuilder;
import eu.europeana.api.client.search.query.Api2QueryInterface;
import eu.europeana.sounds.definitions.model.concept.Concept;
import eu.europeana.sounds.definitions.model.concept.impl.BaseConcept;
import eu.europeana.sounds.definitions.model.concept.impl.MimoMappingView;
import eu.europeana.sounds.definitions.model.vocabulary.MatchTypes;

public class EuropeanaSearchApiClient {

	private static final Log log = LogFactory.getLog(EuropeanaSearchApiClient.class);

	public final static String ONB_INSTRUMENTS_FOLDER = "./src/test/resources/MIMO/onb";
		
	public final static String CSV_DELIMITER = ";";
	 

    /**
     * @param query
     * @return
     * @throws IOException
     * @throws EuropeanaApiProblem
     */
    public List<Concept> loadConceptFromEuropeanaSearchApi(Concept queryConcept) throws IOException, EuropeanaApiProblem {

    	List<Concept> conceptList = new ArrayList<Concept>();
    	    	
		List<Concept> europeanaSearchApiResultsToConceptList = null;
		if (queryConcept.getPrefLabel() != null && queryConcept.getPrefLabel().values() != null 
				&& queryConcept.getPrefLabel().values().toArray() != null) {
	    	String prefLabel = (String)queryConcept.getPrefLabel().values().toArray()[0];
			if (StringUtils.isNotEmpty(prefLabel)){
				europeanaSearchApiResultsToConceptList = parseEuropeanaSearchApiResultsToConcept(queryConcept, prefLabel);
				if (europeanaSearchApiResultsToConceptList != null && europeanaSearchApiResultsToConceptList.size() > 0) {
					conceptList.addAll(europeanaSearchApiResultsToConceptList);
					log.debug("loaded prev label count: " + conceptList.size());
				}
			}
		}
		
		if (queryConcept.getAltLabel() != null) {
			for (Map.Entry<String, String> altLabelEntry : queryConcept.getAltLabel().entrySet()) {
		
				europeanaSearchApiResultsToConceptList = parseEuropeanaSearchApiResultsToConcept(queryConcept, altLabelEntry.getValue());
				if (europeanaSearchApiResultsToConceptList != null && europeanaSearchApiResultsToConceptList.size() > 0) {
		    		conceptList.addAll(europeanaSearchApiResultsToConceptList);
		    		log.debug("loaded alt label count: " + conceptList.size());
				}
			}
		}
		
        return conceptList;
    }

    
	/**
	 * @param queryConcept The original concept object
	 * @param query A pariticular query string for search
	 * @return
	 * @throws UnsupportedEncodingException
	 * @throws MalformedURLException
	 * @throws IOException
	 * @throws EuropeanaApiProblem
	 */
	private List<Concept> parseEuropeanaSearchApiResultsToConcept(Concept queryConcept, String query)
			throws UnsupportedEncodingException, MalformedURLException, IOException, EuropeanaApiProblem {
		
    	List<Concept> conceptList = new ArrayList<Concept>();
        
        //create the query object
        EuropeanaApi2Client europeanaClient = new EuropeanaApi2Client();		
        Api2QueryBuilder queryBuilder = europeanaClient.getQueryBuilder();
		String portalUrl = "http://www.europeana.eu/portal/search?q=";
		
//		String toEncode ="europeana_collectionName:(2059216_Ag_EU_eSOUNDS_1001_ONB)";
		String toEncode ="(europeana_collectionName:(2059216_Ag_EU_eSOUNDS_1001_ONB) AND (title:\"" 
				+ query+ "\" OR proxy_dc_description:\"" + query+ "\"))";
		
		portalUrl += URLEncoder.encode(toEncode, "UTF-8");
		Api2QueryInterface apiQuery = queryBuilder.buildQuery(portalUrl);
		apiQuery.setProfile("rich");
		
		MetadataAccessor ma = new MetadataAccessor(apiQuery, null);
		ma.setStoreItemsAsJson(true);
		ma.setBlockSize(100);
		Map<String, String> contentMapTitle = ma.getContentMap(CommonMetadata.FIELD_TITLE, 1, 1002,
				MetadataAccessor.ERROR_POLICY_CONTINUE);
		for (Map.Entry<String, String> pair : contentMapTitle.entrySet()) {
	    	String europeanaId = pair.getKey();
	    	
	    	MimoMappingView concept = new MimoMappingView();
	    	concept.setUri(queryConcept.getUri());
	    	concept.setPrefLabel(queryConcept.getPrefLabel());
	    	concept.setAltLabel(queryConcept.getAltLabel());
	    	List<String> titleList = new ArrayList<String>(Arrays.asList(pair.getValue().split(";")));
	    	if (titleList != null && titleList.size() > 0)
	    		concept.setTitle(titleList);
	    	if (StringUtils.isNotEmpty(europeanaId))
	    		concept.setEuropeanaId(europeanaId);
	        conceptList.add(concept);
    	}		

		Map<String, String> contentMapDcDescription = ma.getContentMap(CommonMetadata.FIELD_DC_DESCRIPTION, 1, 1002,
				MetadataAccessor.ERROR_POLICY_CONTINUE);

		for (Concept concept : conceptList) {
			List<String> dcDescriptionList = new ArrayList<String>(
			Arrays.asList(contentMapDcDescription.get(((MimoMappingView) concept).getEuropeanaId()).split(";")));
			if (dcDescriptionList != null && dcDescriptionList.size() > 0)
				((MimoMappingView) concept).setDcDescription(dcDescriptionList);
		}
		
        System.out.println("query: " + query + ", added len: " + conceptList.size());
        return conceptList;
	}
    
	
    /**
     * Headers: Searched label; Skos_resource;title; description;europeanaId;
     * @param conceptList
     * @param sFileName
     */
    public void generateSearchConceptCsvFile(List<Concept> conceptList, String sFileName) {

    	try {
    		FileWriter writer = new FileWriter(sFileName);
//    		FileWriter writer = new FileWriter(ONB_INSTRUMENTS_FOLDER + "/" + sFileName);

    		writer.append("Searched label");
    		writer.append(CSV_DELIMITER);
    		writer.append("Skos_resource");
    		writer.append(CSV_DELIMITER);
    		writer.append("title");
    		writer.append(CSV_DELIMITER);
    		writer.append("description");
    		writer.append(CSV_DELIMITER);
    		writer.append("europeanaId");
    		writer.append('\n');

    		Iterator<Concept> itr = conceptList.iterator();
    		while (itr.hasNext()) {
    			MimoMappingView concept = (MimoMappingView) itr.next();
    			writer.append(concept.getPrefLabel().values().toString());
        		writer.append(CSV_DELIMITER);
    			writer.append(concept.getUri());
        		writer.append(CSV_DELIMITER);
        		writer.append(StringUtils.join(concept.getTitle(), ','));
        		writer.append(CSV_DELIMITER);
        		writer.append(StringUtils.join(concept.getDcDescription(), ','));
        		writer.append(CSV_DELIMITER);
        		writer.append(concept.getEuropeanaId());
    			writer.append('\n');
    		}
    		writer.flush();
    		writer.close();
    	} catch (IOException e) {
    		e.printStackTrace();
    	}
    }
    
    
    /**
     * Headers: Prev label; 
     * @param conceptList
     * @param sFileName
     */
    public void generateNotFoundConceptCsvFile(List<Concept> conceptList, String sFileName) {

    	try {
    		FileWriter writer = new FileWriter(sFileName);

    		writer.append("Prev label");
    		writer.append(CSV_DELIMITER);
    		writer.append('\n');

    		Iterator<Concept> itr = conceptList.iterator();
    		while (itr.hasNext()) {
    			BaseConcept concept = (BaseConcept) itr.next();
    			writer.append(concept.getPrefLabel().values().toString());
        		writer.append(CSV_DELIMITER);
    			writer.append('\n');
    		}
    		writer.flush();
    		writer.close();
    	} catch (IOException e) {
    		e.printStackTrace();
    	}
    }
    
    
    /**
     * Headers: URI; 
     * @param conceptList
     * @param sFileName
     */
    public void generateNotFoundUriCsvFile(List<String> uriList, String sFileName) {

    	try {
    		FileWriter writer = new FileWriter(sFileName);

    		writer.append("URI");
    		writer.append(CSV_DELIMITER);
    		writer.append('\n');

    		Iterator<String> itr = uriList.iterator();
    		while (itr.hasNext()) {
    			writer.append(itr.next());
        		writer.append(CSV_DELIMITER);
    			writer.append('\n');
    		}
    		writer.flush();
    		writer.close();
    	} catch (IOException e) {
    		e.printStackTrace();
    	}
    }
    
    
	/**
	 * This method maps ONB items to Europeana search API.
	 * @param conceptList
	 * @param outputFileName
	 * @return Number of mapped concepts
	 * @throws IOException
	 */
	public int mapOnbMimo(List<Concept> conceptList, String outputFileName) throws IOException {
		
		int mappedConceptCount = 0;
		
		List<Concept> enrichedConceptList = new ArrayList<Concept>();
		List<Concept> notEnrichedConceptList = new ArrayList<Concept>();

		// load Europeana Search API Concept data and store it in CSV file
	    for (Concept concept : conceptList) {
		    try {
		    	List<Concept> enrichedConcept = loadConceptFromEuropeanaSearchApi(concept);
		    	if (enrichedConcept != null && enrichedConcept.size() > 0) {
		    		enrichedConceptList.addAll(enrichedConcept);
		    		log.debug("Current enriched concept count: " + enrichedConceptList.size());
		    	} else {
		    		notEnrichedConceptList.add(concept);
		    	}
			} catch (Exception e) {
				log.error("Error by mapping ONB - MIMO using Europeana Search API" + e.getMessage());
	    		notEnrichedConceptList.add(concept);
			}
//		    break;
	    }
	
	    // parse mid and Concept family and store it in CSV comma separated files
	    generateSearchConceptCsvFile(enrichedConceptList, outputFileName);
	    generateNotFoundConceptCsvFile(notEnrichedConceptList, outputFileName.replace("Enriched", "NotEnriched"));

    	mappedConceptCount = enrichedConceptList.size();

    	int notEnrichedConceptCount = notEnrichedConceptList.size();
    	log.debug("Not enriched concept count: " + notEnrichedConceptCount);
    	for (Concept concept : notEnrichedConceptList) {
    		log.debug("Concept: " + concept.getUri() + " could not be enriched!");
    	}

	    return mappedConceptCount;
	}


	/**
	 * This method returns MIMO match by passed URI
	 * @param uri
	 * @param matching type e.g. exact match
	 * @param conceptList
	 * @return match value
	 */
	public String getMatch(String uri, String matchType, List<Concept> conceptList) {

        String res = "";
	    for (Concept concept : conceptList) {
	    	Map<String, String> matchMap = null;
	    	if (matchType.equals(MatchTypes.EXACT.name()))
	    		matchMap = concept.getExactMatch(); 
	    	if (matchType.equals(MatchTypes.BROAD.name())) {
	    		matchMap = concept.getBroadMatch();
	    		if (uri.contains("Clarino"))
	    			res = "";
	    		if (matchMap != null)
	    			res = "";
	    	}
	    	if (matchMap != null && matchMap.size() > 0) {
	    		String conceptUri = concept.getUri();
	    		if (conceptUri.equals(uri)) {
	    			res = matchMap.get(uri + "_" + matchType.toLowerCase() + "Match");
	    			break;
	    		}
	    	}
	    }
        return res;
	}

	
	/**
	 * This method matches ONB items to MIMO and produces a list where matching did not work.
	 * @param inputONBFileName
	 * @param mimoConceptList
	 * @param outputFileName
	 * @param matchType Describes which types of matches should be taken for analysis
	 * @return Number of mapped instruments
	 * @throws IOException
	 */
	public int matchOnbMimo(String inputONBFileName, List<Concept> mimoConceptList, String outputFileName, String matchType) 
			throws IOException {
		
		int mappedConceptCount = 0;
		
	    BufferedReader br=null;
	    BufferedWriter bw=null;
	    final String lineSep=System.getProperty("line.separator");
		List<String> onbNotMatchList = new ArrayList<String>();
		
		int ID_POS = 1; 
    	String splitBy = ";";
	    
		try {
			br = new BufferedReader(new FileReader(inputONBFileName));
			bw = new BufferedWriter(new FileWriter(outputFileName));
			String line = br.readLine();
	    	bw.write(line + splitBy + MatchTypes.EXACT.name() + splitBy + MatchTypes.BROAD.name() + lineSep);
			while ((line = br.readLine()) !=null) {
			    String[] b = line.split(splitBy);
			    if (b.length >= 2 && StringUtils.isNotEmpty(b[ID_POS])) {
			    	String uri = b[ID_POS];
			    	String addedMatch = getMatch(uri, MatchTypes.EXACT.name(), mimoConceptList);
			    	String addedBroadMatch = "";
			    	if (!StringUtils.isNotEmpty(addedMatch)) {
			    		if (!onbNotMatchList.contains(uri))
			    			onbNotMatchList.add(uri);
			    	}
			    	if (matchType.equals(MatchTypes.BROAD.name())) 
			    		addedBroadMatch = getMatch(uri, MatchTypes.BROAD.name(), mimoConceptList);
			    	bw.write(line + splitBy + addedMatch + splitBy + addedBroadMatch + lineSep);
		    		mappedConceptCount = mappedConceptCount + 1;
			    }
			}
	        if(br!=null)
	            br.close();
	        if(bw!=null)
	            bw.close();
		} catch (FileNotFoundException e1) {
			log.error("File not found. " + e1.getMessage());
			e1.printStackTrace();
		} catch (IOException e) {
			log.error("IO error. " + e.getMessage());
			e.printStackTrace();
		}
	
		generateNotFoundUriCsvFile(onbNotMatchList, outputFileName.replace("match", "not_match"));

    	log.debug("Matched instruments count: " + mappedConceptCount);
    	log.debug("Not matching instruments count: " + onbNotMatchList.size());
    	for (String instrument : onbNotMatchList) {
    		log.debug("Instrument URI: " + instrument + " could not be matched!");
    	}

	    return mappedConceptCount;
	}

	
}
