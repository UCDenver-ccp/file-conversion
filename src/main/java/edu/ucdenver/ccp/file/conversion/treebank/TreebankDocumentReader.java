package edu.ucdenver.ccp.file.conversion.treebank;

/*
 * #%L
 * Colorado Computational Pharmacology's file conversion
 * 						project
 * %%
 * Copyright (C) 2012 - 2019 Regents of the University of Colorado
 * %%
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * 3. Neither the name of the Regents of the University of Colorado nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Iterator;

import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.fit.component.ViewCreatorAnnotator;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.factory.TypeSystemDescriptionFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.cleartk.corpus.penntreebank.PennTreebankReader;
import org.cleartk.corpus.penntreebank.TreebankGoldAnnotator;
import org.cleartk.syntax.constituent.type.TreebankNode;

import edu.ucdenver.ccp.common.file.CharacterEncoding;
import edu.ucdenver.ccp.common.io.StreamUtil;
import edu.ucdenver.ccp.file.conversion.DocumentReader;
import edu.ucdenver.ccp.file.conversion.TextDocument;
import edu.ucdenver.ccp.nlp.core.annotation.TextAnnotation;
import edu.ucdenver.ccp.nlp.core.annotation.TextAnnotationFactory;
import edu.ucdenver.ccp.nlp.core.mention.impl.DefaultClassMention;

public class TreebankDocumentReader extends DocumentReader {

	@Override
	public TextDocument readDocument(String sourceId, String sourceDb, InputStream inputStream,
			InputStream documentTextStream, CharacterEncoding encoding) throws IOException {

		try {
			TypeSystemDescription tsd = TypeSystemDescriptionFactory
					.createTypeSystemDescription("org.cleartk.syntax.constituent.TypeSystem");

			AnalysisEngine treebankGoldAnnotator = AnalysisEngineFactory
					.createEngine(TreebankGoldAnnotator.getDescription(), TreebankGoldAnnotator.PARAM_POST_TREES, true);

			JCas jCas = JCasFactory.createJCas(tsd);
			String documentText = StreamUtil.toString(new InputStreamReader(documentTextStream, encoding.getDecoder()));
			jCas.setDocumentText(documentText);

			JCas treebankView = ViewCreatorAnnotator.createViewSafely(jCas, PennTreebankReader.TREEBANK_VIEW);
			String treebank = StreamUtil.toString(new InputStreamReader(inputStream, encoding.getDecoder()));
			treebankView.setSofaDataString(treebank, "text/plain");
			treebankGoldAnnotator.process(jCas);
			treebankGoldAnnotator.collectionProcessComplete();

			TextDocument td = new TextDocument(sourceId, sourceDb, documentText);
			TextAnnotationFactory factory = TextAnnotationFactory.createFactoryWithDefaults(sourceId);

			for (Iterator<TreebankNode> nodeIter = JCasUtil.iterator(jCas, TreebankNode.class); nodeIter.hasNext();) {
				TreebankNode node = nodeIter.next();

				int begin = node.getBegin();
				int end = node.getEnd();
				String nodeType = node.getNodeType();
				@SuppressWarnings("unused")
				String nodeValue = node.getNodeValue();
				@SuppressWarnings("unused")
				boolean isLeaf = node.getLeaf();

				if (!nodeType.equals("TOP")) {
					TextAnnotation annot = factory.createAnnotation(begin, end, documentText.substring(begin, end),
							new DefaultClassMention(nodeType));
					td.addAnnotation(annot);
				}
			}

			return td;
		} catch (UIMAException e) {
			throw new IOException(e);
		}

	}

}
