/**
 * Example SMART REST Application: Parses OAuth tokens from
 * browser-supplied header, then provides a list of which prescriptions
 * will need to be refilled soon (based on dispense days supply + date)
 *
 * Josh Mandel
 * Children's Hospital Boston, 2010
 *
 * Translated from python to Java by Nate Finstein
 */

package uk.ac.dotrural.trump.indivowf.test;

import java.io.OutputStream;
import java.io.IOException;

import java.util.GregorianCalendar;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;

import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletConfig;
import org.openrdf.query.QueryLanguage;

import org.openrdf.repository.RepositoryConnection;
import org.openrdf.query.TupleQuery;
import org.openrdf.query.TupleQueryResult;
import org.openrdf.query.BindingSet;

import org.smartplatforms.client.SmartClient;
import org.smartplatforms.client.SmartClientException;
import org.smartplatforms.client.SmartOAuthParser;
import org.smartplatforms.client.SmartResponse;
import org.smartplatforms.client.TokenSecret;

/**
 * Servlet implementation class TestClient
 * Basically just stealing
 */
public class TestClient extends HttpServlet {
	
	/**
	 * Store user credentials for accessing (this makes this a security bottleneck... ideally should be stored and managed securely)
	 */
	private Map<String, SmartOAuthParser> userCredentials;
	
	private String lastUserRecordID;
	
	String reminderHeader = "<!DOCTYPE html>\n<html><head>"
			+ "<script src=\"http://sample-apps.smartplatforms.org/framework/smart/scripts/smart-api-client.js\">"
			+ "</script><title>java generated</title></head>\n<body>\n";

	String reminderFooter = "</body></html>";

        private ServletConfig sConfig = null;
	private DatatypeFactory dtf = null;

	private String sparqlForReminders = "PREFIX dcterms:<http://purl.org/dc/terms/>\n"
			+ "PREFIX sp:<http://smartplatforms.org/terms#>\n"
			+ "PREFIX rdf:<http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n"
			+ "   SELECT  ?med ?name ?quant ?when\n"
			+ "   WHERE {\n"
			+ "          ?med rdf:type sp:Medication .\n"
			+ "          ?med sp:drugName ?medc.\n"
			+ "          ?medc dcterms:title ?name.\n"
			+ "          ?med sp:fulfillment ?fill.\n"
			+ "          ?fill sp:dispenseDaysSupply ?quant.\n"
			+ "          ?fill dcterms:date ?when.\n" + "   }";


	@Override
	public void init() throws ServletException {
	    System.out.println("in init() for Reminder");
	    
	    userCredentials = new HashMap<String, SmartOAuthParser>();
	    
	    this.sConfig = getServletConfig();
	    
	    try {
		dtf = DatatypeFactory.newInstance();
	    } catch (javax.xml.datatype.DatatypeConfigurationException dce) {
		throw new ServletException(dce);
	    }
	}

	@Override
	public void doGet(HttpServletRequest req, HttpServletResponse res)
			throws ServletException {
		System.out.println("in doGet() for Reminder  --  " + req.getPathInfo());
		String pathInfo = req.getPathInfo();
		if (pathInfo.equals("/index.html")) {
			presentReminders(req, res);
		}
		if (pathInfo.equals("/users.html")) {
			showUsers(req, res);
		}
	}

	private void showUsers(HttpServletRequest req, HttpServletResponse res) throws ServletException
	{
		try {
			OutputStream resOut = res.getOutputStream();
			resOut.write(reminderHeader.getBytes());
			
			SmartClient sc = new SmartClient()
			
			for(Entry<String, SmartOAuthParser> e : userCredentials.entrySet())
			{
				resOut.write(new String(e.getKey() + " : " + e.getValue() + "<br/>").getBytes());
			}
			
			resOut.write(reminderFooter.getBytes());
			
			resOut.close();
			
		} catch (IOException ioe) {
			throw new ServletException(ioe);
		}
		
			
	}
	
	private void presentReminders(HttpServletRequest req,
			HttpServletResponse res) throws ServletException {

		try {
			OutputStream resOut = res.getOutputStream();
			resOut.write(reminderHeader.getBytes());
		} catch (IOException ioe) {
			throw new ServletException(ioe);
		}

		SmartOAuthParser authParams = new SmartOAuthParser(req);
		String recordId = authParams.getParam("smart_record_id");
		TokenSecret tokenSecret = new TokenSecret(authParams);
		
		userCredentials.put(recordId, authParams);
		lastUserRecordID = recordId;
		
		Map<String, GregorianCalendar> pillDates = new HashMap<String, GregorianCalendar>();

		// Represent the list as an RDF graph
		try {
            // Example of using filters and pagination parameters:
            //
            // Map<String,Object> params = new HashMap <String,Object> ();
            // params.put ("limit","5");
            // params.put ("offset", "0");
            
            Map<String,Object> params = null;
        
			SmartClient client = new SmartClient(
							     authParams.getParam("oauth_consumer_key"),
							     sConfig.getInitParameter("consumerSecret"),
							     authParams.getParam("smart_container_api_base"));

                        SmartResponse resObj = client
					.get_medications(recordId, tokenSecret, params);
                        RepositoryConnection meds = resObj.graph;

			String pillWhen = null;
			String pillQuant = null;
			String pillName = null;
			try {
				TupleQuery tq = meds.prepareTupleQuery(QueryLanguage.SPARQL,
						sparqlForReminders);
				TupleQueryResult tqr = tq.evaluate();
				while (tqr.hasNext()) {
					BindingSet bns = tqr.next();
					// "   SELECT  ?med ?name ?quant ?when\n" +
					pillWhen = bns.getValue("when").stringValue();
					pillQuant = bns.getValue("quant").stringValue();
					pillName = bns.getValue("name").stringValue();

					XMLGregorianCalendar xgreg = dtf
							.newXMLGregorianCalendar(pillWhen);
					GregorianCalendar greg = xgreg.toGregorianCalendar();
					greg.add(GregorianCalendar.DAY_OF_YEAR,
							new Float(pillQuant).intValue());

					GregorianCalendar priorWhen = pillDates.get(pillName);
					if (priorWhen == null || priorWhen.before(greg)) {
						pillDates.put(pillName, greg);
					}
				}
			} catch (org.openrdf.repository.RepositoryException rex) {
				throw new ServletException(rex);
			} catch (org.openrdf.query.MalformedQueryException mqx) {
				throw new ServletException(mqx);
			} catch (org.openrdf.query.QueryEvaluationException qvx) {
				throw new ServletException(qvx);
			}

			Iterator<String> medNames = pillDates.keySet().iterator();
			StringBuffer retStrb = new StringBuffer();
                        Boolean late = false;
			GregorianCalendar today = new GregorianCalendar();
			while (medNames.hasNext()) {
				String aMed = medNames.next();
				GregorianCalendar dayFromMap = pillDates.get(aMed);
				if (today.after(dayFromMap)) {
					retStrb.append("<i>LATE!</i> ");
				}
				String xmlFormatDate = dtf.newXMLGregorianCalendar(dayFromMap).toXMLFormat();
				retStrb.append(aMed + ": <b>" + xmlFormatDate.substring(0, 10)
						+ "</b><br>");
                                late = true;
			}
			if (retStrb.length() == 0) {
				retStrb.append("Up to date on all meds.");
			}

			try {
				OutputStream resOut = res.getOutputStream();
				if (late) resOut.write("Refills due!<br><br>".getBytes());
				resOut.write(retStrb.toString().getBytes());
				resOut.write(reminderFooter.getBytes());
				resOut.close();
			} catch (IOException ioe) {
				throw new ServletException(ioe);
			}

		} catch (SmartClientException sme) {
			System.out.println("sme:::: " + sme.getClass().getName());
			throw new ServletException(sme);
		}
	}
	
}
