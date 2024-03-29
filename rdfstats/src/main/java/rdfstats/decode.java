/**
 * Copyright 2008-2009 Institute for Applied Knowledge Processing, Johannes Kepler University Linz
 *  
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package rdfstats;

import java.io.FileOutputStream;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.TimeZone;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import at.jku.rdfstats.RDFStatsConfiguration;
import at.jku.rdfstats.RDFStatsDataset;
import at.jku.rdfstats.RDFStatsModel;
import at.jku.rdfstats.RDFStatsModelException;
import at.jku.rdfstats.RDFStatsModelFactory;
import at.jku.rdfstats.RDFStatsModelImpl;
import at.jku.rdfstats.hist.Histogram;
import at.jku.rdfstats.hist.builder.HistogramBuilderException;
import at.jku.rdfstats.html.GenerateHTML;
import at.jku.rdfstats.vocabulary.Stats;

import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Property;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.util.FileManager;

/**
 * @author dorgon
 *
 * Decodes and ouputs a statistics document to screen.
 * 
 * For real applications, please use the API.
 * 
 */
public class decode {
	private static Options opts = new Options();
	private static final Log log = LogFactory.getLog(decode.class);

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		Option input = new Option("i", "in", true, "Input RDF file or Web URI");
		input.setArgName("filename");
		Option format = new Option("f", "format", true, "Input format (RDF/XML, N3, or N-TRIPLES), default: auto-detect based on file extension");
		format.setArgName("format");

		Option endpoint = new Option("e", "endpoint", true, "Only print statistics for this endpoint URI");
		endpoint.setArgName("endpoint-uri");
		Option document = new Option("d", "document", true, "Only print statistics for this document URL");
		document.setArgName("document-url");
		
		Option config = new Option("c", "config", true, "Configuration file");
		config.setArgName("filename");
		Option timeZone = new Option("t", "timezone", true, "The time zone to use when parsing date values (default is your locale: " + TimeZone.getDefault().getDisplayName() + ")");
		timeZone.setArgName("timezone");
		Option html = new Option("h", "html", true, "Generate output into HTML");
		html.setArgName("filename");
		
		opts = new Options();
		opts.addOption(input);
		opts.addOption(format);
		opts.addOption(endpoint);
		opts.addOption(document);
		opts.addOption(config);
		opts.addOption(timeZone);
		opts.addOption(html);
		
		// create the parser
	    CommandLineParser parser = new BasicParser();
	    try {
	        CommandLine cmd = parser.parse(opts, args);
	        
	        // requires input file or config file
	        if (cmd.hasOption("i") || cmd.hasOption("c")) {

	        	try {
	        		RDFStatsConfiguration cfg;
	        		if (cmd.hasOption("c")) {
	        			log.info("Using configuration from '" + cmd.getOptionValue("c"));
	        			Model cfgModel = FileManager.get().loadModel(cmd.getOptionValue("c"));
	        			cfg = RDFStatsConfiguration.create(cfgModel);
	        		} else if (cmd.hasOption("i")) {
	        			log.info("Using input file '" + cmd.getOptionValue("i") + "'.");
	        			
	        			Model m;
		        		if (cmd.hasOption("f")) 
		        			m = FileManager.get().loadModel(cmd.getOptionValue("i"), cmd.getOptionValue("f"));
		        		else
		        			m = FileManager.get().loadModel(cmd.getOptionValue("i"));
		        		
		        		cfg = RDFStatsConfiguration.create(
	        					m,
	        					null,
	        					null,
	        					null,
	        					null,
	        					null,
	        					null,
	        					false,
	        					cmd.hasOption("t") ? TimeZone.getTimeZone(cmd.getOptionValue("t")) : null);
	        		} else cfg = RDFStatsConfiguration.getDefault(); // will not occur
	        		
	        		String endpointUri = cmd.getOptionValue("e");
	        		String documentUrl = cmd.getOptionValue("d");
	        		
	        		RDFStatsModel stats = RDFStatsModelFactory.create(cfg.getStatsModel());
	        		for (RDFStatsDataset ds : stats.getDatasets()) {
        				if (cmd.hasOption("h")) {
        					String htmlString = GenerateHTML.generateHTML(stats);
        					FileWriter out = new FileWriter(cmd.getOptionValue("h"));
        					out.write(htmlString);
        					out.close();
        				} else if (endpointUri == null && documentUrl == null || // printStatistics either if no specific endpointUri/documentUrl selected or the matching one
	        				ds.getSourceType().equals(Stats.SPARQLEndpoint) && endpointUri != null && endpointUri.trim() == (ds.getSourceUrl()) ||
	        				ds.getSourceType().equals(Stats.RDFDocument) && documentUrl != null && documentUrl.trim() == (ds.getSourceUrl())) {
	        				printStatistics(stats, ds);
	        			}
	        		}
	        	} catch (Exception e) {
	        		log.error(e.getMessage(), e);
	        	}
		    } else {
		    	printUsage("Invalid arguments. Please specify at least a config file or input filename to read from");
		    }
	    } catch( ParseException exp ) {
	    	printUsage(null);
	    	return;
	    }
	}

	private static void printStatistics(RDFStatsModel stats, RDFStatsDataset dataset) throws RDFStatsModelException, HistogramBuilderException {
		String url = dataset.getSourceUrl();
		SimpleDateFormat df = new SimpleDateFormat();

		if (url != null)
			System.out.println("--- Statistics for " + dataset.getSourceType() + " <" + url + ">--------------------------------");
		else
			System.out.println("--- Statistics --------------------------------");
		
		System.out.println("Created by: " + dataset.getCreator() + " on " + df.format(dataset.getDate()) + "\n");
		
//		if (stats.storesTypeSpecificSubjectHistograms(url)) {
//			for (String c : stats.getSubjectHistogramClassess(url)) {
//				System.out.println("--- subject histogram for class <" + c + "> ---------------");
//				Histogram<String> hist = stats.getSubjectHistogram(url, c);
//				System.out.println(hist.toString());
//			}
//			
//			Histogram<String> hist = stats.getSubjectHistogram(url, null);
//			if (hist != null) {
//				System.out.println("--- subjects histogram for untyped subjects ---------------");
//				System.out.println(hist.toString());
//			}
//		} else {
			Histogram<String> hist = stats.getSubjectHistogram(url, false);
			if (hist != null) {
				System.out.println("--- URI subject histogram ---------------");
				System.out.println(hist.toString());
			}

			hist = stats.getSubjectHistogram(url, true);
			if (hist != null) {
				System.out.println("--- anonymous subject histogram (bnodes) ---------------");
				System.out.println(hist.toString());
			}
//		}

//		if (stats.storesTypeSpecificPropertyHistograms(url)) {
//			for (String c : stats.getPropertyHistogramClasses(url)) {
//				// for each property, range
//				for (String p : stats.getPropertyHistogramProperties(url, c)) {
//					for (String r : stats.getPropertyHistogramRanges(url, c, p)) {
//						try {
//							Histogram<?> hist = stats.getPropertyHistogram(url, c, p, r);
//							System.out.println("--- property histogram for... --------------------");
//							System.out.println("class: <" + c + ">\n" +
//									"property: <" + p + ">\n" +
//									"range: <" + r + ">");
//							if (hist.hasUniqueValues())
//								System.out.println("unique: the values of the source distribution are unique");
//
//							System.out.println(hist.toString());
//						} catch (RDFStatsModelException ex) {
//							log.error("Failed to decode histogram.", ex);
//						}
//					}
//				}
//			}
//	
//			// histograms for untyped resources
//			for (String p : stats.getPropertyHistogramProperties(url, null)) {
//				// for each property, range
//				for (String r : stats.getPropertyHistogramRanges(url, null, p)) {
//					try {
//						Histogram<?> hist = stats.getPropertyHistogram(url, null, p, r);
//						System.out.println("--- property histogram for... -------------------");
//						System.out.println("untyped subjects (no rdf:type property)\n" +
//								"property: <" + p + ">\n" +
//								"range: <" + r + ">");
//						if (hist.hasUniqueValues())
//							System.out.println("unique: the values of the source distribution are unique");
//						
//						System.out.println(hist.toString());
//					} catch (RDFStatsModelException ex) {
//						log.error("Failed to decode histogram.", ex);
//					}
//				}
//			}
//		} else {
			// type-independent property histograms
			for (String p : stats.getPropertyHistogramProperties(url)) {
				// for each property, range
				for (String r : stats.getPropertyHistogramRanges(url, p)) {
					try {
						Histogram<?> h = stats.getPropertyHistogram(url, p, r);
						System.out.println("--- property histogram for <" + p + "> -------------------\n" +
								"range: <" + r + ">");
						if (h.hasUniqueValues())
							System.out.println("unique: the values of the source distribution are unique");
						
						System.out.println(h.toString());
					} catch (RDFStatsModelException ex) {
						log.error("Failed to decode histogram.", ex);
					}
				}
			}
//		}
	}

	private static void printUsage(String msg) {
		System.out.println("RDFStats " + RDFStatsConfiguration.getVersion() + " (C)2008, Institute for Applied Knowledge Processing, J. Kepler University Linz, Austria");
    	System.out.println("Decodes a statistics document and prints information to screen. To use the decoder in your application, please use the class at.jku.rdfstats.RDFStatsModel.");
		if (msg != null) System.out.println(msg + '\n');
		HelpFormatter formatter = new HelpFormatter();
		formatter.printHelp("parameters: ", opts);
	}

}
