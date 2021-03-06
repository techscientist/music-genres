package eu.europeana.sounds.utils.concept;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.ResIterator;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.rdf.model.Statement;
import com.hp.hpl.jena.rdf.model.StmtIterator;
import com.hp.hpl.jena.util.FileManager;

import eu.europeana.sounds.definitions.model.WebAnnotationFields;
import eu.europeana.sounds.definitions.model.concept.Concept;
import eu.europeana.sounds.definitions.model.concept.impl.BaseConcept;
import eu.europeana.sounds.definitions.model.vocabulary.ConceptTypes;


/**
 * This class implements methods for working with SKOS. 
 *
 */
public class SkosUtils {
	
	private static Logger log = Logger.getRootLogger();

	String REGULAR_FREEBASE_PREFIX = "/m/";
	String FILE_FORM_FREEBASE_PREFIX = "m.";
	String WIKIDATA_ID_CLOSE_MATCH_KEY = "wikidataId_closeMatch";
	String WIKIDATA_BASE_URL = "https://www.wikidata.org/wiki/Q";
	String CLOSE_MATCH_PREDICATE_URL = "http://www.w3.org/2004/02/skos/core#closeMatch";
	String RDF_RES_FILE_NAME = "model.rdf";
	public String WIKIDATA_ID_KEY = "wikidataId";
	public String DBPEDIA_ID_KEY = "dbpediaId";
	public String EN = "en";
	
	
    /**
     * This method performs parsing of the SKOS RDF in XML format to Europeana Annotation Concept object using Jena library.
     * @param inputFileName
     * @return Concept object
     */
    public Concept parseSkosRdfXmlToConcept(String inputFileName) {
    	
    	Model model = createModelFromRdfFile(inputFileName);

    	// write it to standard out
    	model.write(System.out);    
    	
    	// retrieve the statements from the model
    	return retrieveStatements(model);
    }
	
    
    /**
     * This method performs parsing of the SKOS RDF in XML format to Europeana Annotation Concept collection using Jena library.
     * @param inputFileName
     * @return A collection of the Concept objects
     */
    public List<Concept> parseSkosRdfXmlToConceptCollection(String inputFileName) {
    	
    	Model model = createModelFromRdfFile(inputFileName);

    	// write it to standard out
    	model.write(System.out);    
    	
    	// retrieve the statements from the model
    	return retrieveConcepts(model);
    }
	
    
    /**
     * This method performs parsing of the SKOS flat file in CSV format to 
     * Europeana Annotation Concept collection.
     * @param inputFileName
     * @return A collection of the Concept objects
     */
    public List<Concept> retrieveConceptsFromCsv(String inputFileName) {
	        
		List<Concept> res = new ArrayList<Concept>();
		
		int ID_POS = 1; 
		int LABEL_POS = 0; 
    	String splitBy = ";";
	    
	    BufferedReader br;
		try {
			br = new BufferedReader(new FileReader(inputFileName));
			String line = "";
			while ((line = br.readLine()) !=null) {
		    	BaseConcept concept = new BaseConcept();
		    	concept.addType(ConceptTypes.SKOS_CONCEPT.name());
			    String[] b = line.split(splitBy);
			    if (b.length >= 2 && StringUtils.isNotEmpty(b[ID_POS]) && StringUtils.isNotEmpty(b[LABEL_POS])) {
			    	String uri = b[ID_POS];
			    	concept.setUri(uri);
			    	String[] chunks = uri.split("/");
			    	String id = chunks[chunks.length-1];
				    concept.addPrefLabelInMapping(id, b[LABEL_POS]);
				    res.add(concept);
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


	/**
	 * This method returns concept object if already in the list
	 * or creates new object if not exists.
	 * @param uri
	 * @param conceptList
	 * @return concept object
	 */
	Concept getConcept(String uri, List<Concept> conceptList) {
		
		BaseConcept concept = new BaseConcept();
		
		for (Concept curConcept : conceptList) {
			if (curConcept.getUri().equals(uri))
				return curConcept;
		}
		
    	concept.addType(ConceptTypes.SKOS_CONCEPT.name());
		return concept;
	}

	
	/**
	 * This method checks whether a given concept already exists in a passed list.
	 * @param concept
	 * @param conceptList
	 * @return true if exists, false if not
	 */
	boolean conceptExists(Concept concept, List<Concept> conceptList) {
		
		for (Concept curConcept : conceptList) {
			if (curConcept.getUri().equals(concept.getUri()))
				return true;
		}
		return false;
	}

	
    /**
     * This method performs parsing of the MIMO matches file in CSV format to 
     * Europeana Annotation Concept collection.
     * @param inputFileName
     * @return A collection of the Concept objects
     */
    public List<Concept> retrieveMatchesFromCsv(String inputFileName) {
	        
		List<Concept> res = new ArrayList<Concept>();
		
		int URI_POS = 0; 
		int EXACT_POS = 1; 
		int CLOSE_POS = 2; 
		int BROAD_POS = 3; 
		int NARROW_POS = 4; 
    	String splitBy = ";";
	    
	    BufferedReader br;
		try {
			br = new BufferedReader(new FileReader(inputFileName));
			String line = br.readLine();			
			while ((line = br.readLine()) !=null) {
			    String[] b = line.split(splitBy);
			    if (b.length >= 1 && StringUtils.isNotEmpty(b[URI_POS])) {
			    	String uri = b[URI_POS];
			    	uri = uri.replace("#", "").replace(" ", "+");
			    	Concept concept = getConcept(uri, res);
			    	concept.setUri(uri);
//			    	String[] chunks = uri.split("/");
//			    	String id = chunks[chunks.length-1];
			    	String id = uri;
				    if (b.length > EXACT_POS && StringUtils.isNotEmpty(b[EXACT_POS]))
				    	concept.addExactMatchInMapping(id, b[EXACT_POS]);
				    if (b.length > CLOSE_POS && StringUtils.isNotEmpty(b[CLOSE_POS]))
				    	concept.addCloseMatchInMapping(id, b[CLOSE_POS]);
				    if (b.length > BROAD_POS && StringUtils.isNotEmpty(b[BROAD_POS]))
				    	concept.addBroadMatchInMapping(id, b[BROAD_POS]);
				    if (b.length > NARROW_POS && StringUtils.isNotEmpty(b[NARROW_POS]))
				    	concept.addNarrowMatchInMapping(id, b[NARROW_POS]);
				    if (!conceptExists(concept, res))
				    		res.add(concept);
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

    
    /**
     * This method performs parsing of the MIMO matches file in CSV format to 
     * Europeana Annotation Concept collection.
     * @param inputFileName
     * @return A collection of the Concept objects
     */
    public List<Concept> retrieveCompositionConceptsFromCsv(String inputFileName) {
	        
		List<Concept> res = new ArrayList<Concept>();
		
		int MID_POS = 0; 
		int NAME_POS = 2; 
		int IS_CLASSIC_POS = 3; 
		int TYPE_POS = 6; 
		int SUBTYPE_POS = 7; 
    	String splitBy = ";";
	    
	    BufferedReader br;
		try {
			br = new BufferedReader(new FileReader(inputFileName));
			String line = br.readLine();			
			while ((line = br.readLine()) !=null) {
			    String[] b = line.split(splitBy);
			    if (b.length >= 1 && StringUtils.isNotEmpty(b[MID_POS])) {
			    	String mid = b[MID_POS];
				    if (b.length > IS_CLASSIC_POS && StringUtils.isNotEmpty(b[IS_CLASSIC_POS]) 
				    		&& b[IS_CLASSIC_POS].equals("yes")) {
				    	String uri = "";
					    if (b.length > NAME_POS && StringUtils.isNotEmpty(b[NAME_POS]))
					    	uri = mid; 
				    	Concept concept = getConcept(uri, res);
				    	concept.setUri(uri);
				    	List<String> types = new ArrayList<String>();
					    if (b.length > TYPE_POS && StringUtils.isNotEmpty(b[TYPE_POS])) {
					    	types.add(b[TYPE_POS]);
					    }
					    if (b.length > SUBTYPE_POS && StringUtils.isNotEmpty(b[SUBTYPE_POS])) {
					    	types.add(b[SUBTYPE_POS]);
					    }
					    if (types.size() > 0)
					    	concept.setType(types);
					    if (!conceptExists(concept, res))
				    		res.add(concept);
				    }
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

    
	/**
	 * @param orig
	 * @return
	 */
	public String setPredicate(String orig) {
		String res = "";
		res = matchPredicate(orig);
		return res;
	}
	
	/**
	 * The predicate value is obtained from RDF statement. This method retrieves the field value of the Concept object. E.g. 'prefLabel'.
	 * Sample: http://www.w3.org/2004/02/skos/core#prefLabel
	 * @param parsed predicate value
	 * @return field name of the Concept object
	 */
	private String matchPredicate(String value) {
		String res = "";
		String regex = "#(.*)$"; // we find any character sequence at the end of the line, after '#'
		Pattern pattern = Pattern.compile(regex);
		Matcher matcher = pattern.matcher(value);
		if (matcher.find()) {
		    res = matcher.group(0).substring(1); // cut first element
		}
		return res;
	}
	
	
	/**
	 * @param concept
	 * @param predicate
	 * @param conceptFieldName
	 * @param value
	 */
	@SuppressWarnings("unchecked")
	public void fillConcept(
			BaseConcept concept, String predicate, String conceptFieldName, String value) {

    	String methodName = WebAnnotationFields.ADD + conceptFieldName.substring(0, 1).toUpperCase() + conceptFieldName.substring(1);
    	String getMethodName = WebAnnotationFields.GET + conceptFieldName.substring(0, 1).toUpperCase() + conceptFieldName.substring(1);

    	Method method = null;
		Method getMethod = null;

	    try {
		    getMethod = concept.getClass().getMethod(getMethodName);
		    Class<?> returnType = getMethod.getReturnType();
		    if (returnType.equals(Map.class)) {
				method = concept.getClass().getMethod(methodName + WebAnnotationFields.IN_MAPPING, String.class, String.class);
			} else {
				method = concept.getClass().getMethod(methodName, String.class);
			}

		    if (method != null) {
			    if (returnType.equals(Map.class)) {
		    		Object valueMap = getMethod.invoke(concept);
		    		// counter is necessary to have different IDs in map for the same linguistic key
		    		int counter = 0;
		    		if (valueMap != null) {
		    			counter = ((Map<String,String>) valueMap).size();
		    		}
				    method.invoke(concept, predicate + counter, value);
		    	} else {
		  		    method.invoke(concept, value);            		
		    	}
		    }
		} catch (IllegalArgumentException e) {
        	log.error("IllegalArgumentException error by filling Concept object from SKOS RDF/XML model for Concept field: " + conceptFieldName);
		} catch (IllegalAccessException e) {
        	log.error("IllegalAccessException error by filling Concept object from SKOS RDF/XML model for Concept field: " + conceptFieldName);
		} catch (InvocationTargetException e) {
        	log.error("InvocationTargetException error by filling Concept object from SKOS RDF/XML model for Concept field: " + conceptFieldName);
		} catch (SecurityException e) {
        	log.error("SecurityException error by filling Concept object from SKOS RDF/XML model for Concept field: " + conceptFieldName);
		} catch (NoSuchMethodException e) {
        	log.error("NoSuchMethodException error by filling Concept object from SKOS RDF/XML model for Concept field: " + conceptFieldName);
        }
		
	}

	
	/**
	 * @param model
	 * @return
	 */
	public BaseConcept retrieveStatements(Model model) {
		StmtIterator itr = model.listStatements();
    	BaseConcept concept = new BaseConcept();
    	concept.addType(ConceptTypes.SKOS_CONCEPT.name());
    	    	    	
    	while (itr.hasNext()) {
    		Statement statement = itr.next();
        	Triple triple = statement.asTriple();
        	if (StringUtils.isEmpty(concept.getUri())) {
        		concept.setUri(triple.getSubject().toString());
        	}

        	String predicate = triple.getPredicate().toString();
        	String conceptFieldName = setPredicate(predicate);
        	
        	String value = triple.getObject().toString();
        	log.info("Statement: " + predicate + " = " + value);
        	
    		fillConcept(concept, predicate, conceptFieldName, value);
    	}
		return concept;
	}

	
	/**
	 * This method extracts FreebaseId from a Concept field 'closeMatch'.
	 * It takes first valid value from the closeMatch map.
	 * @param concept
	 * @return FreebaseId
	 */
	public String extractFreebaseIdFromConceptCloseMatch(Concept concept) {	
		String closeMatch = "";
		if (concept != null && concept.getCloseMatch() != null && concept.getCloseMatch().size() > 0) {
			for (Object value : concept.getCloseMatch().values()) {
				closeMatch = (String) value;
				break;
			}
		}
		String freebaseId = "";
		if (closeMatch != null && closeMatch.contains("/")) {
			String[] chunks = closeMatch.split("/");
			freebaseId = chunks[chunks.length-1];
		}
		return freebaseId;
	}
		
	
	/**
	 * This method extracts DBPedia ID from a Concept field 'exactMatch'.
	 * It takes first valid value from the closeMatch map.
	 * @param concept
	 * @return dbpediaId
	 */
	public String extractDBPediaIdFromConceptExactMatch(Concept concept) {	
		String exactMatch = "";
		if (concept != null && concept.getExactMatch() != null && concept.getExactMatch().size() > 0) {
			for (Object value : concept.getExactMatch().values()) {
				exactMatch = (String) value;
				break;
			}
		}
		String dbpediaId = "";
//		if (exactMatch != null && exactMatch.contains("/")) {
//			String[] chunks = exactMatch.split("/");
//			dbpediaId = chunks[chunks.length-1];
//		}
		if (exactMatch != null && exactMatch.contains("@")) {
			String[] chunks = exactMatch.split("@");
			dbpediaId = chunks[0].replace("\"", "");
		}
		return dbpediaId;
//		return exactMatch;
	}
	
	
	/**
	 * Text is separated by given splitter string.
	 * @param text
	 * @param splitter
	 * @return last chunk
	 */
	public String getLastChunk(String text, String splitter) {
		String lastChunk = "";
		if (text != null && text.contains(splitter)) {
			String[] chunks = text.split(splitter);
			lastChunk = chunks[chunks.length-1];
		}
        return lastChunk;
	}
		
	
	/**
	 * This method converts Freebase ID from the file name form e.g. 'm.xyz'
	 * into a regular Freebase ID form e.g. '/m/xyz'.
	 * @param freebaseId
	 * @return normalized Freebase ID
	 */
	public String normalizeFreebaseId(String freebaseId) {
		return freebaseId.replace(FILE_FORM_FREEBASE_PREFIX, REGULAR_FREEBASE_PREFIX);
	}
	

	/**
	 * This method converts Freebase ID from a regular Freebase ID form e.g. '/m/xyz' 
	 * into the file name form e.g. 'm.xyz'.
	 * @param freebaseId
	 * @return file name Freebase ID
	 */
	public String freebaseIdToFileName(String freebaseId) {
		return freebaseId.replace(REGULAR_FREEBASE_PREFIX, FILE_FORM_FREEBASE_PREFIX);
	}
	
	
	/**
	 * This method queries Wikidata by given FreebaseId and returns Wikidata ID.
	 *     e.g. https://wdq.wmflabs.org/api?q=string[646:%27/m/0557q%27]
	 *     where 646 is a number of Freebase ID property in Wikidata namespace.
	 * @param freebaseId
	 * @return Wikidata ID
	 */
	public String queryWikidataIdByFreebaseId(String freebaseId) {
		String wikidataId = "";
		return wikidataId;
	}

	
	/**
	 * This method retrieves concepts from a model.
	 * @param model
	 * @return
	 */
	public List<Concept> retrieveConcepts(Model model) {
		List<Concept> res = new ArrayList<Concept>();
		
		ResIterator itrConcepts = model.listSubjects();
		while (itrConcepts.hasNext()) {
			Resource rdfNode = itrConcepts.next();
        	System.out.println("rdfNode: " + rdfNode.toString());
        	Property p = null;
        	String object = null;
			StmtIterator itr = model.listStatements(rdfNode, p, object);

	    	BaseConcept concept = new BaseConcept();
	    	concept.addType(ConceptTypes.SKOS_CONCEPT.name());

	    	while (itr.hasNext()) {
	    		Statement statement = itr.next();
	        	Triple triple = statement.asTriple();
	        	if (StringUtils.isEmpty(concept.getUri())) {
	        		concept.setUri(triple.getSubject().toString());
	        	}
	
	        	String predicate = triple.getPredicate().toString();
	        	String conceptFieldName = setPredicate(predicate);
	        	
	        	String value; 
	        	
	        	if(triple.getObject().isLiteral())
	        		value = (String) triple.getObject().getLiteralValue();
	        	else 
	        		value = triple.getObject().toString(false);
	        	
	        	log.info("Statement: " + predicate + " = " + value);
	        	System.out.println("Statement: " + predicate + " = " + value);
	        	
	    		fillConcept(concept, predicate, conceptFieldName, value);
	    	}
	    	res.add(concept);
		}
		return res;
	}

	
	/**
	 * This method finds a concept by it's URL.
	 * @param concepts The list of concepts
	 * @param url The query URL
	 * @return concept object
	 */
	private Concept getConceptByUrl(List<Concept> concepts, String url) {
		Concept res = null;
		Iterator<Concept> itr = concepts.iterator();
		while (itr.hasNext()) {
			Concept concept = itr.next();
			if (concept.getUri().equals(url)) {
				res = concept;
			    break;
			}
		}
		return res;
	}
	
	
	/**
	 * @param source
	 * @param description_begin_str
	 * @param description_end_str
	 * @return
	 */
	public String parseHtmlField(String source, String description_begin_str, String description_end_str) {
		
		String description = "";
		int descriptionIndex = source.indexOf(description_begin_str);
		if (descriptionIndex > -1) {
			int description_begin_pos = descriptionIndex + description_begin_str.length();
			int description_end_pos = source.substring(description_begin_pos).indexOf(description_end_str);
			description = source.substring(
					description_begin_pos
					, description_end_pos + description_begin_pos);
		}
		return description;
	}
	

	/**
	 * This method generates initial RDF file for given concepts.
	 * @param concepts The list of concepts
	 * @param inputFilePath The original RDF file
	 */
	public boolean generateRdfForConcepts(List<Concept> concepts, String inputFileName, String pathToAnalysisFolder) 
				throws IOException {
			
		boolean res = false;
		
		File queryResultsFile = new File(pathToAnalysisFolder, inputFileName);
		// create parent dirs
		queryResultsFile.getParentFile().mkdirs();
		BufferedWriter writer = null;
		try {
			writer = new BufferedWriter(new FileWriter(queryResultsFile));
			String header = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" 
							+ "<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"\n"
							+ "\t\txmlns:skos=\"http://www.w3.org/2004/02/skos/core#\">\n\n";
			writer.write(header);
			for (Concept concept : concepts) {
				BaseConcept currentConcept = (BaseConcept) concept;
				String prefLabelRdf = "";
				if (currentConcept.getPrefLabel() != null) {
					for (Map.Entry<String, String> prefLabel : currentConcept.getPrefLabel().entrySet()) {
						prefLabelRdf = prefLabelRdf + "\t\t<skos:prefLabel xml:lang=\"" 
							+ prefLabel.getKey().replace("_prefLabel", "") + "\">" + prefLabel.getValue() + "</skos:prefLabel>\n";
					}
				}
				String closeMatchRdf = "";
				if (currentConcept.getCloseMatch() != null) {
					closeMatchRdf = "\t\t<skos:closeMatch rdf:resource=\"http://www.wikidata.org/entity/Q" 
							+ currentConcept.getCloseMatch().get(WIKIDATA_ID_KEY + "_" + WebAnnotationFields.CLOSE_MATCH) +"\"/>\n";
					String dbpediaKey = DBPEDIA_ID_KEY + "_" + WebAnnotationFields.CLOSE_MATCH;
				    if (currentConcept.getCloseMatch().containsKey(dbpediaKey))
				    	closeMatchRdf = closeMatchRdf + "\t\t<skos:closeMatch rdf:resource=\"" 
				    			+ currentConcept.getCloseMatch().get(dbpediaKey) +"\"/>\n";
				}
				String definitionRdf = "";
				if (currentConcept.getDefinition() != null) 
					definitionRdf = "\t\t<skos:definition xml:lang=\"en\">" 
						+ currentConcept.getDefinition().get(EN + "_" + WebAnnotationFields.DEFINITION) + "</skos:definition>\n";
				String inSchemeRdf = "";
				if (currentConcept.getType() != null) {
					for (String type : currentConcept.getType())
						inSchemeRdf = "\t\t<skos:inScheme rdf:resource=\"" + type + "\"/>\n";
				}
				
				String conceptRdf = "\t<skos:Concept rdf:about=\"http://rdf.freebase.com/ns" 
						+ currentConcept.getUri() + "\">\n"						
						+ prefLabelRdf
						+ closeMatchRdf
						+ definitionRdf
						+ inSchemeRdf
    					+ "\t</skos:Concept>\n\n";
				writer.write(conceptRdf);
			}
			String end = "\n\n</rdf:RDF>";
			writer.write(end);
		} finally {
			try {
				writer.close();
			} catch (IOException e) {
				log.warn("cannot close results writer for file: "
						+ queryResultsFile);
			}
			res = true;
		}
		return res;
	}
	
	
	public String parseDescriptionStr(String description) {
		String descriptionStr = "";
		if (description.length() > 1) {
			if (description.contains("#") && description.length() > 1) {
				String[] descriptionParts = description.split("#");
				int DESCRIPTION_ID = 1;
				descriptionStr = descriptionParts[DESCRIPTION_ID];
			} else {
				descriptionStr = description;
			}
		}
		return descriptionStr;
	}

	
	public String parseDescriptionKey(String description) {
		String descriptionKey = "";
		if (description.length() > 1) {
			if (description.contains("#") && description.length() > 1) {
				String[] descriptionParts = description.split("#");
				int DBPEDIA_ID = 0;
				descriptionKey = descriptionParts[DBPEDIA_ID];
			}
		}
		return descriptionKey;
	}
	
    
	public String getNameByFreebaseUriFromOrigCsv(String freebaseUri, String inputFileName, String pathToAnalysisFolder) {

		String name = "";
		
		int MID_POS = 0; 
		int NAME_POS = 2; 
		String splitBy = ";";

		BufferedReader br;
		try {
			br = new BufferedReader(new FileReader(pathToAnalysisFolder + inputFileName));
			br.readLine();	// header line
			String line = "";
			while ((line = br.readLine()) !=null) {
			    String[] b = line.split(splitBy);
			    String freebaseId = "";
			    if (b.length >= 1 && StringUtils.isNotEmpty(b[MID_POS])) {
			    	freebaseId = b[MID_POS];
					if (freebaseUri.equals(freebaseId)) {
						name = b[NAME_POS];
						break;
					}
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
		return name;
	}
	
	
	/**
	 * This method extends existing composition CSV file by description and Wikidata ID.
	 * @param concepts The list of concepts
	 * @param inputFilePath The original CSV file
	 */
	public boolean generateCsvForConcepts(List<Concept> concepts, String inputFileName, String outputFileName
											, String pathToAnalysisFolder) 
				throws IOException {
			
		boolean res = false;
		
    	String splitBy = ";";
	    
    	List<String> originalLines = new ArrayList<String>();
	    BufferedReader br;
		try {
			br = new BufferedReader(new FileReader(pathToAnalysisFolder + inputFileName));
			String headerLine = br.readLine();	
			originalLines.add(headerLine);
			String line = "";
			while ((line = br.readLine()) !=null) {
				originalLines.add(line);
			}
		    br.close();
		} catch (FileNotFoundException e1) {
			log.error("File not found. " + e1.getMessage());
			e1.printStackTrace();
		} catch (IOException e) {
			log.error("IO error. " + e.getMessage());
			e.printStackTrace();
		}

		
		File queryResultsFile = new File(pathToAnalysisFolder, outputFileName);
		// create parent dirs
		queryResultsFile.getParentFile().mkdirs();
		BufferedWriter writer = null;
		try {
			writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(pathToAnalysisFolder + outputFileName), "UTF-8"));            
			Iterator<String> itrOrigLines = originalLines.iterator();
			int MID_POS = 0; 
			int count = 0;
			while (itrOrigLines.hasNext()) {
				String line = itrOrigLines.next();
				if (count == 0) {
					writer.write(line + ";Wikidata ID;DBPedia ID;Description;\n");
					count = count + 1;
				} else {
				    String description = "";
				    String wikidataId = "";
					for (Concept concept : concepts) {
						BaseConcept currentConcept = (BaseConcept) concept;
					    String[] b = line.split(splitBy);
					    String freebaseId = "";
					    if (b.length >= 1 && StringUtils.isNotEmpty(b[MID_POS])) {
					    	freebaseId = b[MID_POS];
							if (currentConcept.getUri().equals(freebaseId)) {
								if (currentConcept.getDefinition() != null) 
									description = currentConcept.getDefinition().get(EN + "_" + WebAnnotationFields.DEFINITION);
								if (currentConcept.getCloseMatch() != null) 
									wikidataId = currentConcept.getCloseMatch().get(WIKIDATA_ID_KEY + "_" + WebAnnotationFields.CLOSE_MATCH);
							}
					    }
					}
					writer.write(line + ";" + wikidataId + ";" 
							+ parseDescriptionKey(description) + ";" + parseDescriptionStr(description) + ";\n");
				}
			}
		} finally {
			try {
				writer.close();
			} catch (IOException e) {
				log.warn("cannot close results writer for file: "
						+ queryResultsFile);
			}
			res = true;
		}
		return res;
	}
	
	
	/**
	 * This method adds concepts to a model retrieved from original RDF file 
	 * and stores it in enriched RDF file.
	 * @param concepts The list of concepts
	 * @param inputFileName The original RDF file
	 * @param pathToAnalysisFolder The location for enriched RDF file
	 */
	public boolean writeConceptsToRdf(List<Concept> concepts, String inputFileName, String pathToAnalysisFolder) {

		boolean res = false;
		
    	Model model = createModelFromRdfFile(inputFileName);
		
		ResIterator itrConcepts = model.listSubjects();
		while (itrConcepts.hasNext()) {
			Resource rdfNode = itrConcepts.next();
        	System.out.println("rdfNode: " + rdfNode.toString());
        	Property p = null;
        	String object = null;
			StmtIterator itr = model.listStatements(rdfNode, p, object);
	    	
	    	while (itr.hasNext()) {
	    		Statement statement = itr.next();
	        	Triple triple = statement.asTriple();
	        	Concept curConcept = getConceptByUrl(concepts, triple.getSubject().toString());
		        if (curConcept.getCloseMatch() != null 
		        		&& StringUtils.isNotEmpty(curConcept.getCloseMatch().get(WIKIDATA_ID_CLOSE_MATCH_KEY))) {
    				// set subject, predicate, object
	        		Resource subject = statement.getSubject();
	        		Property predicate = ResourceFactory.createProperty(CLOSE_MATCH_PREDICATE_URL);
	        		Resource rdfObject = ResourceFactory.createResource(
	        				WIKIDATA_BASE_URL +
	        				curConcept.getCloseMatch().get(WIKIDATA_ID_CLOSE_MATCH_KEY));
	        		Statement newStatement = ResourceFactory.createStatement(subject, predicate, rdfObject);
       				model.add(newStatement);
       				break;
	        	}	
	    	}
		}
		
    	// write model to standard out
//    	model.write(System.out);
		res = writeModelToFile(model, pathToAnalysisFolder + RDF_RES_FILE_NAME);
		
		return res;
	}


	/**
	 * This method saves updated Jena Model to a file
	 * @param model
	 * @param fileName
	 */
	public boolean writeModelToFile(Model model, String fileName) {
		
		boolean res = false;
		
		FileWriter out = null;
		try {
			out = new FileWriter( fileName );
		    model.write( out, "RDF/XML-ABBREV" );
		    res = true;
		} catch (IOException closeException) {
		       // ignore
		} finally {
		    try {
				out.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}	
		
		return res;
	}
	
	
	/**
	 * This method loads data from given RDF file in a model.
	 * @param inputFileName The path to the RDF file
	 * @return model
	 */
	private Model createModelFromRdfFile(String inputFileName) {
		// create an empty model
   	    Model model = ModelFactory.createDefaultModel();

    	// use the FileManager to find the input file
    	InputStream in = FileManager.get().open(inputFileName);
    	
    	if (in == null) {
    	    throw new IllegalArgumentException(
    	                                 "File: " + inputFileName + " not found");
    	}

    	// read the RDF/XML file
    	model.read(in, null);
		return model;
	}

	
}
