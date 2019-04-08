package edu.ucdenver.ccp.file.conversion.conllu;

/*-
 * #%L
 * Colorado Computational Pharmacology's file conversion
 * 						project
 * %%
 * Copyright (C) 2019 Regents of the University of Colorado
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

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import edu.ucdenver.ccp.common.file.CharacterEncoding;
import edu.ucdenver.ccp.common.file.FileComparisonUtil;
import edu.ucdenver.ccp.common.file.FileComparisonUtil.ColumnOrder;
import edu.ucdenver.ccp.common.file.FileComparisonUtil.LineOrder;
import edu.ucdenver.ccp.common.file.FileComparisonUtil.LineTrim;
import edu.ucdenver.ccp.common.file.FileComparisonUtil.ShowWhiteSpace;
import edu.ucdenver.ccp.common.file.FileReaderUtil;
import edu.ucdenver.ccp.common.io.ClassPathUtil;
import edu.ucdenver.ccp.common.io.StreamUtil;
import edu.ucdenver.ccp.file.conversion.TextDocument;
import edu.ucdenver.ccp.nlp.core.annotation.TextAnnotation;

public class CoNLLURoundTripTest {

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Test
	public void testRoundTripConversion() throws IOException {
		CharacterEncoding encoding = CharacterEncoding.UTF_8;
		/*
		 * load token annotations with dependency relations from a sample file
		 * on the classpath
		 */
		InputStream conllUStream = ClassPathUtil.getResourceStreamFromClasspath(getClass(), "doc1.conllu");
		InputStream docTextStream = ClassPathUtil.getResourceStreamFromClasspath(getClass(), "doc1.txt");
		String documentText = StreamUtil
				.toString(new InputStreamReader(docTextStream, CharacterEncoding.UTF_8.getDecoder()));
		List<TextAnnotation> annotations = CoNLLUDocumentReader.getAnnotations(conllUStream, documentText,
				CharacterEncoding.UTF_8);

		File docTextFile = folder.newFile("12345.txt");
		ClassPathUtil.copyClasspathResourceToFile(getClass(), "doc1.txt", docTextFile);

		/*
		 * write those annotations to CoNLL format - there is some loss here
		 * (lemmas) so we will use the generated file to compare against
		 * downstream
		 */
		File origConlluFile = folder.newFile("12345.conllu");
		CoNLLUDocumentWriter.serializeAnnotations(annotations, origConlluFile, CharacterEncoding.UTF_8);

		/* read the CoNLL-U file */
		TextDocument td = new CoNLLUDocumentReader().readDocument("12345", "PMC", origConlluFile, docTextFile,
				encoding);

		/* write the CoNLL-U file again */
		File roundTripConlluFile = folder.newFile("12345.rt.conllu");
		new CoNLLUDocumentWriter().serialize(td, roundTripConlluFile, encoding);

		/* compare the two written CoNLL-U files */
		List<String> expectedLines = FileReaderUtil.loadLinesFromFile(origConlluFile, encoding);
		List<String> observedLines = FileReaderUtil.loadLinesFromFile(roundTripConlluFile, encoding);

		assertTrue(FileComparisonUtil.hasExpectedLines(observedLines, expectedLines, null, LineOrder.AS_IN_FILE,
				ColumnOrder.AS_IN_FILE, LineTrim.OFF, ShowWhiteSpace.OFF));

	}

}
