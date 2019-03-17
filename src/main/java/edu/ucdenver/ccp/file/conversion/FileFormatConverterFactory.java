package edu.ucdenver.ccp.file.conversion;

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
import java.util.ArrayList;
import java.util.List;

import edu.ucdenver.ccp.file.conversion.bionlp.BioNLPDocumentReader;
import edu.ucdenver.ccp.file.conversion.bionlp.BioNLPDocumentWriter;
import edu.ucdenver.ccp.file.conversion.brat.BratDocumentWriter;
import edu.ucdenver.ccp.file.conversion.conllcoref2012.CoNLLCoref2012DocumentReader;
import edu.ucdenver.ccp.file.conversion.conllcoref2012.CoNLLCoref2012DocumentWriter;
import edu.ucdenver.ccp.file.conversion.conllcoref2012.CoNLLCoref2012DocumentWriter.IncludeCorefType;
import edu.ucdenver.ccp.file.conversion.conllu.CoNLLUDocumentReader;
import edu.ucdenver.ccp.file.conversion.conllu.CoNLLUDocumentWriter;
import edu.ucdenver.ccp.file.conversion.knowtator.KnowtatorDocumentReader;
import edu.ucdenver.ccp.file.conversion.knowtator2.Knowtator2DocumentReader;
import edu.ucdenver.ccp.file.conversion.knowtator2.Knowtator2DocumentWriter;
import edu.ucdenver.ccp.file.conversion.pubannotation.PubAnnotationDocumentReader;
import edu.ucdenver.ccp.file.conversion.pubannotation.PubAnnotationDocumentWriter;
import edu.ucdenver.ccp.file.conversion.sentence.SentenceDocumentWriter;
import edu.ucdenver.ccp.file.conversion.treebank.SentenceTokenOnlyTreebankDocumentReader;
import edu.ucdenver.ccp.file.conversion.treebank.TreebankDocumentReader;
import edu.ucdenver.ccp.file.conversion.uima.UimaDocumentWriter;

public class FileFormatConverterFactory {

	// public static final Map<InputFileFormat, Set<OutputFileFormat>>
	// validInputOutputMap = createValidInputOutputMap();
	//
	// private static Map<InputFileFormat, Set<OutputFileFormat>>
	// createValidInputOutputMap() {
	// Map<InputFileFormat, Set<OutputFileFormat>> map = new
	// HashMap<InputFileFormat, Set<OutputFileFormat>>();
	//
	// map.put(InputFileFormat.BIONLP, EnumSet.of(OutputFileFormat., ));
	// }

	public static DocumentReader getReader(InputFileFormat sourceFormat) {
		switch (sourceFormat) {
		case BIONLP:
			return new BioNLPDocumentReader();
		case CONLL_COREF_2012:
			return new CoNLLCoref2012DocumentReader();
		case CONLL_U:
			return new CoNLLUDocumentReader();
		case KNOWTATOR:
			return new KnowtatorDocumentReader();
		case KNOWTATOR2:
			return new Knowtator2DocumentReader();
		case PUBANNOTATION:
			return new PubAnnotationDocumentReader();
		case TREEBANK:
			return new TreebankDocumentReader();
		case TREEBANK_SENTENCE_TOKEN:
			return new SentenceTokenOnlyTreebankDocumentReader();
		default:
			throw new IllegalArgumentException(
					"Unhandled source file format: " + sourceFormat.name() + ". Code changes required.");
		}
	}

	public static DocumentWriter getWriter(OutputFileFormat targetFormat) {
		switch (targetFormat) {
		case BIONLP:
			return new BioNLPDocumentWriter();
		case BRAT:
			return new BratDocumentWriter();
		case CONLL_COREF_2012_IDENT:
			return new CoNLLCoref2012DocumentWriter(IncludeCorefType.IDENT);
		case CONLL_COREF_2012_APPOS:
			return new CoNLLCoref2012DocumentWriter(IncludeCorefType.APPOS);
		case CONLL_U:
			return new CoNLLUDocumentWriter();
		case KNOWTATOR2:
			return new Knowtator2DocumentWriter();
		case PUBANNOTATION:
			return new PubAnnotationDocumentWriter();
		case UIMA:
			return new UimaDocumentWriter();
		case SENTENCE:
			return new SentenceDocumentWriter();
		default:
			throw new IllegalArgumentException(
					"Unhandled target file format: " + targetFormat.name() + ". Code changes required.");
		}
	}

	public static FileFormatConverter getConverter(List<InputFileFormat> sourceFormats, OutputFileFormat targetFormat)
			throws IOException {
		List<DocumentReader> documentReaders = new ArrayList<DocumentReader>();
		for (InputFileFormat sourceFormat : sourceFormats) {
			documentReaders.add(getReader(sourceFormat));
		}
		DocumentWriter documentWriter = getWriter(targetFormat);
		return new FileFormatConverter(documentReaders, documentWriter);
	}

}
