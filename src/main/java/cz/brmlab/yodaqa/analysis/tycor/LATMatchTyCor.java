package cz.brmlab.yodaqa.analysis.tycor;

import java.util.HashMap;
import java.util.Map;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cz.brmlab.yodaqa.model.CandidateAnswer.AnswerInfo;
import cz.brmlab.yodaqa.model.TyCor.LAT;

/**
 * Estimate answer specificity in CandidateAnswerCAS via type coercion
 * by question LAT to answer LAT matching. We simply try to find the
 * most specific LAT match. */

public class LATMatchTyCor extends JCasAnnotator_ImplBase {
	final Logger logger = LoggerFactory.getLogger(LATMatchTyCor.class);

	protected class LATMatch {
		public LAT lat1, lat2;
		public double specificity;

		public LATMatch(LAT lat1_, LAT lat2_) {
			lat1 = lat1_;
			lat2 = lat2_;
			specificity = lat1.getSpecificity() + lat2.getSpecificity();
		}

		public LAT getLat1() { return lat1; }
		public LAT getLat2() { return lat2; }
		public double getSpecificity() { return specificity; }
	}

	public void initialize(UimaContext aContext) throws ResourceInitializationException {
		super.initialize(aContext);
	}

	public void process(JCas jcas) throws AnalysisEngineProcessException {
		JCas questionView, answerView;
		try {
			questionView = jcas.getView("Question");
			answerView = jcas.getView("Answer");
		} catch (Exception e) {
			throw new AnalysisEngineProcessException(e);
		}

		LATMatch match = matchLATs(questionView, answerView);
		if (match != null) {
			AnswerInfo ai = JCasUtil.selectSingle(answerView, AnswerInfo.class);
			ai.setSpecificity(match.getSpecificity());
		}
	}

	protected LATMatch matchLATs(JCas questionView, JCas answerView) throws AnalysisEngineProcessException {
		Map<String, LAT> answerLats = new HashMap<String, LAT>();
		LATMatch bestMatch = null;

		/* Load LATs from answerView. */
		for (LAT la : JCasUtil.select(answerView, LAT.class))
			answerLats.put(la.getText(), la);
		if (answerLats.isEmpty())
			return null;

		/* Match LATs from questionView. */
		for (LAT lq : JCasUtil.select(questionView, LAT.class)) {
			LAT la = answerLats.get(lq.getText());
			if (la == null)
				continue;
			LATMatch match = new LATMatch(lq, la);
			if (bestMatch == null || match.getSpecificity() > bestMatch.getSpecificity())
				bestMatch = match;
		}

		if (bestMatch != null)
			logger.debug(".. TyCor "
					+ bestMatch.getLat1().getBase().getCoveredText()
					+ "-" + bestMatch.getLat2().getBase().getCoveredText()
					+ " LAT " + bestMatch.getLat1().getText()
					+ " sp. " + bestMatch.getSpecificity());
		return bestMatch;
	}
}