package edu.ucdenver.ccp.file.conversion.roundtrip;

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

import org.junit.Before;
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
import edu.ucdenver.ccp.file.conversion.bionlp.BioNLPDocumentReader;
import edu.ucdenver.ccp.file.conversion.bionlp.BioNLPDocumentWriter;
import edu.ucdenver.ccp.file.conversion.brat.BratDocumentReader;
import edu.ucdenver.ccp.file.conversion.brat.BratDocumentWriter;
import edu.ucdenver.ccp.file.conversion.conllu.CoNLLUDocumentReader;
import edu.ucdenver.ccp.file.conversion.conllu.CoNLLUDocumentWriter;
import edu.ucdenver.ccp.file.conversion.knowtator2.Knowtator2DocumentReader;
import edu.ucdenver.ccp.file.conversion.knowtator2.Knowtator2DocumentWriter;
import edu.ucdenver.ccp.file.conversion.pubannotation.PubAnnotationDocumentReader;
import edu.ucdenver.ccp.file.conversion.pubannotation.PubAnnotationDocumentWriter;
import edu.ucdenver.ccp.nlp.core.annotation.TextAnnotation;

/**
 * test conversion of a document from format to format, testing that the final
 * copy of the original format is as expected.
 */
public class RoundTripConversionTest {

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();
	private CharacterEncoding encoding;
	private TextDocument td;
	private File origConlluFile;
	private File docTextFile;

	/**
	 * Loads a CoNLL-U file so that we have annotations and relations to use
	 * during the round-trip tests. Reading CoNLL is potentially a lossy
	 * round-trip b/c we may read in the lemma field, but we don't store that
	 * data, so when writing a CoNLL file the lemma field will be blank. Because
	 * of this the setUp routine reads then writes then reads again before
	 * initializing a {@link TextDocument} to use for the round-trip tests.
	 * 
	 * @throws IOException
	 */
	@Before
	public void setUp() throws IOException {
		encoding = CharacterEncoding.UTF_8;
		/*
		 * load token annotations with dependency relations from a sample file
		 * on the classpath
		 */
		InputStream conllUStream = ClassPathUtil.getResourceStreamFromClasspath(getClass(), "11532192.conllu");
		InputStream docTextStream = ClassPathUtil.getResourceStreamFromClasspath(getClass(), "11532192.txt");
		String documentText = StreamUtil
				.toString(new InputStreamReader(docTextStream, CharacterEncoding.UTF_8.getDecoder()));
		List<TextAnnotation> annotations = CoNLLUDocumentReader.getAnnotations(conllUStream, documentText,
				CharacterEncoding.UTF_8);

		docTextFile = folder.newFile("11532192.txt");
		ClassPathUtil.copyClasspathResourceToFile(getClass(), "11532192.txt", docTextFile);

		origConlluFile = folder.newFile("11532192.conllu");
		CoNLLUDocumentWriter.serializeAnnotations(annotations, origConlluFile, CharacterEncoding.UTF_8);

		td = new CoNLLUDocumentReader().readDocument("11532192", "PMC", origConlluFile, docTextFile, encoding);
	}

	@Test
	public void testRoundTrip_conllu() throws IOException {
		File roundTrippedConllFile = folder.newFile("roundtrip.conllu");
		new CoNLLUDocumentWriter().serialize(td, roundTrippedConllFile, encoding);

		List<String> expectedLines = FileReaderUtil.loadLinesFromFile(origConlluFile, encoding);
		List<String> observedLines = FileReaderUtil.loadLinesFromFile(roundTrippedConllFile, encoding);

		assertTrue(FileComparisonUtil.hasExpectedLines(observedLines, expectedLines, null, LineOrder.AS_IN_FILE,
				ColumnOrder.AS_IN_FILE, LineTrim.OFF, ShowWhiteSpace.OFF));
	}

	@Test
	public void testRoundTrip_bionlp() throws IOException {
		/* write a BioNLP file */
		File bionlpFile = folder.newFile("11532192.bionlp");
		new BioNLPDocumentWriter().serialize(td, bionlpFile, encoding);

		/* read the BioNLP file */
		TextDocument roundTripTd = new BioNLPDocumentReader().readDocument("11532192", "PMC", bionlpFile, docTextFile,
				encoding);

		/* write the CoNLL-U file again */
		File conlluFile = folder.newFile("11532192.rt.conllu");
		new CoNLLUDocumentWriter().serialize(roundTripTd, conlluFile, encoding);

		/* compare the two written CoNLL-U files */
		List<String> expectedLines = FileReaderUtil.loadLinesFromFile(origConlluFile, encoding);
		List<String> observedLines = FileReaderUtil.loadLinesFromFile(conlluFile, encoding);

		assertTrue(FileComparisonUtil.hasExpectedLines(observedLines, expectedLines, null, LineOrder.AS_IN_FILE,
				ColumnOrder.AS_IN_FILE, LineTrim.OFF, ShowWhiteSpace.OFF));

	}

	@Test
	public void testRoundTrip_pubannotation() throws IOException {

		/* write a PubAnnotation file */
		File pubannotationFile = folder.newFile("11532192.json");
		new PubAnnotationDocumentWriter().serialize(td, pubannotationFile, encoding);

		/* read the PubAnnotation file */
		TextDocument roundTripTd = new PubAnnotationDocumentReader().readDocument("11532192", "PMC", pubannotationFile,
				docTextFile, encoding);

		/* write the CoNLL-U file again */
		File conlluFile = folder.newFile("11532192.rt.conllu");
		new CoNLLUDocumentWriter().serialize(roundTripTd, conlluFile, encoding);

		/* compare the two written CoNLL-U files */
		List<String> expectedLines = FileReaderUtil.loadLinesFromFile(origConlluFile, encoding);
		List<String> observedLines = FileReaderUtil.loadLinesFromFile(conlluFile, encoding);

		assertTrue(FileComparisonUtil.hasExpectedLines(observedLines, expectedLines, null, LineOrder.AS_IN_FILE,
				ColumnOrder.AS_IN_FILE, LineTrim.OFF, ShowWhiteSpace.OFF));
	}

	@Test
	public void testRoundTrip_knowtator2() throws IOException {
		/* write a Knowtator2 file */
		File knowtator2File = folder.newFile("11532192.xml");
		new Knowtator2DocumentWriter().serialize(td, knowtator2File, encoding);

		/* read the Knowator2 file */
		TextDocument roundTripTd = new Knowtator2DocumentReader().readDocument("11532192", "PMC", knowtator2File,
				docTextFile, encoding);

		/* write the CoNLL-U file again */
		File conlluFile = folder.newFile("11532192.rt.conllu");
		new CoNLLUDocumentWriter().serialize(roundTripTd, conlluFile, encoding);

		/* compare the two written CoNLL-U files */
		List<String> expectedLines = FileReaderUtil.loadLinesFromFile(origConlluFile, encoding);
		List<String> observedLines = FileReaderUtil.loadLinesFromFile(conlluFile, encoding);

		assertTrue(FileComparisonUtil.hasExpectedLines(observedLines, expectedLines, null, LineOrder.AS_IN_FILE,
				ColumnOrder.AS_IN_FILE, LineTrim.OFF, ShowWhiteSpace.OFF));
	}

	@Test
	public void testRoundTrip_brat() throws IOException {
		/* write a BRAT annotation file */
		File bratFile = folder.newFile("11532192.ann");
		new BratDocumentWriter().serialize(td, bratFile, encoding);

		/* read the BRAT file */
		TextDocument roundTripTd = new BratDocumentReader().readDocument("11532192", "PMC", bratFile, docTextFile,
				encoding);

		/* write the CoNLL-U file again */
		File conlluFile = folder.newFile("11532192.rt.conllu");
		new CoNLLUDocumentWriter().serialize(roundTripTd, conlluFile, encoding);

		/* compare the two written CoNLL-U files */
		List<String> expectedLines = FileReaderUtil.loadLinesFromFile(origConlluFile, encoding);
		List<String> observedLines = FileReaderUtil.loadLinesFromFile(conlluFile, encoding);

		assertTrue(FileComparisonUtil.hasExpectedLines(observedLines, expectedLines, null, LineOrder.AS_IN_FILE,
				ColumnOrder.AS_IN_FILE, LineTrim.OFF, ShowWhiteSpace.OFF));
	}

}
