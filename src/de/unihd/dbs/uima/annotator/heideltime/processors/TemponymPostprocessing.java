package de.unihd.dbs.uima.annotator.heideltime.processors;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

import org.apache.uima.cas.FSIterator;
import org.apache.uima.jcas.JCas;

import de.unihd.dbs.uima.annotator.heideltime.utilities.Toolbox;
import de.unihd.dbs.uima.types.heideltime.Timex3;
import de.unihd.dbs.uima.types.heideltime.Timex3Interval;

/**
 * 
 * This class removes TIMEX3 annotations for temponyms and adds
 * TIMEX3INTERVAL annotations containing (earliest|latest)(Begin|End) information.
 * @author jannik stroetgen
 *
 */
public class TemponymPostprocessing {
	
	public static void handleIntervals(JCas jcas){
		
		// used to collect temponym timexes
		HashSet<Timex3> timexes = new HashSet<>();
		
		// iterate over all TEMPONYMS
		FSIterator iterTimex = jcas.getAnnotationIndex(Timex3.type).iterator();
		while (iterTimex.hasNext()) {
			Timex3 t = (Timex3) iterTimex.next();
			if (t.getTimexType().equals("TEMPONYM")) {
				
				// create a timex3interval for each temponym
				Timex3Interval ti = new Timex3Interval(jcas);

				System.err.println("TEMPONYM: " + t.getCoveredText());
				
				ti.setBegin(t.getBegin());
				ti.setEnd(t.getEnd());
				ti.setTimexType(t.getTimexType());
				ti.setAllTokIds(t.getAllTokIds());
				ti.setTimexFreq(t.getTimexFreq());
				ti.setTimexMod(t.getTimexMod());
				ti.setTimexQuant(t.getTimexQuant());
				// set a new id
				String id = t.getTimexId();
				int newId = Integer.parseInt(id.replace("t", ""));
				newId += 100000;
				ti.setTimexId("t" + newId);

				// get the (earliest|last)(begin|end) information
				Pattern p = Pattern.compile("\\[(.*?), (.*?), (.*?), (.*?)\\]");
				for (MatchResult mr : Toolbox.findMatches(p,t.getTimexValue())) {
					ti.setTimexValueEB(mr.group(1));
					ti.setTimexValueLB(mr.group(2));
					ti.setTimexValueEE(mr.group(3));
					ti.setTimexValueLE(mr.group(4));	
				}

				// Aside from the begin/end designations the temponym value may contain
				// an array with one or more identifying references, e.g. URIs
				// These ids are catenated with '|' as that is a character that would
				// be percent-encoded in any URI.
				List<String> refStrings = parseReferencesFromTimexValue(t.getTimexValue());
				if (!refStrings.isEmpty()) {
					ti.setRef(String.join("|",  refStrings));
				}

				//System.err.println("temponym: " + t.getTimexValue());				
				if ((ti.getTimexValueEB() == ti.getTimexValueLB()) && 
						(ti.getTimexValueLB() == ti.getTimexValueEE()) &&
						(ti.getTimexValueEE() == ti.getTimexValueLE())) {
					ti.setTimexValue(ti.getTimexValueEB());
					t.setTimexValue(ti.getTimexValueEB());
				}
				else { // what's the best single value for an interval!?
					t.setEmptyValue(t.getTimexValue());
					ti.setTimexValue(ti.getTimexValueLE());
					t.setTimexValue(ti.getTimexValueLE());
				}
				ti.setFoundByRule(t.getFoundByRule());
				ti.addToIndexes();
				timexes.add(t);
			}
		}

		// shall the standard timexes really be removed?
		for (Timex3 t : timexes){
			t.removeFromIndexes();
		}
	}


	private static List<String> parseReferencesFromTimexValue(String timexValue) {
		List<String> result = new LinkedList<String>();
		// Match one or more single-quoted strings in an array, eg: "['id1','id2']"
		Pattern p = Pattern.compile("\\[((\\'[^\\']+\\',* *)+)\\]");
		for (MatchResult mr : Toolbox.findMatches(p, timexValue)) {
			// Split on the single-quotes (optionally followed by comma-separators)
			// to get reference id strings, e.g. "id1" and "id2"
			// NOTE: This assumes, that none of the references contain single quotes
			for (String s : mr.group(1).split("\\',* *\\'?")) {
				if (s.trim().length() > 0) {
					result.add(s);
				}
			}
		}
		return result;
	}
}
