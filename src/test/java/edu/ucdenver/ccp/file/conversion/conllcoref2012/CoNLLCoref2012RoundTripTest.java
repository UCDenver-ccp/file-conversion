package edu.ucdenver.ccp.file.conversion.conllcoref2012;

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

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
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
import edu.ucdenver.ccp.file.conversion.TextDocument;
import edu.ucdenver.ccp.file.conversion.conllcoref2012.CoNLLCoref2012DocumentWriter.IncludeCorefType;

public class CoNLLCoref2012RoundTripTest {

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Test
	public void testRoundTrip() throws IOException {
		CharacterEncoding encoding = CharacterEncoding.UTF_8;
		InputStream conllStream = ClassPathUtil.getResourceStreamFromClasspath(getClass(), "sample-craft.ident.conll");
		InputStream documentTextStream = ClassPathUtil.getResourceStreamFromClasspath(getClass(), "sample-craft.txt");

		TextDocument td = new CoNLLCoref2012DocumentReader().readDocument("11532192", "PMID", conllStream,
				documentTextStream, encoding);

		File originalConllFile = folder.newFile("orig.conll");
		ClassPathUtil.copyClasspathResourceToFile(getClass(), "sample-craft.ident.conll", originalConllFile);

		File roundTrippedConllFile = folder.newFile("roundtrip.conll");
		new CoNLLCoref2012DocumentWriter(IncludeCorefType.IDENT).serialize(td, roundTrippedConllFile, encoding);

		List<String> expectedLines = FileReaderUtil.loadLinesFromFile(originalConllFile, encoding);
		List<String> observedLines = FileReaderUtil.loadLinesFromFile(roundTrippedConllFile, encoding);

		assertTrue(FileComparisonUtil.hasExpectedLines(observedLines, expectedLines, null, LineOrder.AS_IN_FILE,
				ColumnOrder.AS_IN_FILE, LineTrim.OFF, ShowWhiteSpace.OFF));

		// go around again...

		conllStream = new FileInputStream(roundTrippedConllFile);
		documentTextStream = ClassPathUtil.getResourceStreamFromClasspath(getClass(), "sample-craft.txt");

		td = new CoNLLCoref2012DocumentReader().readDocument("11532192", "PMID", conllStream, documentTextStream,
				encoding);

		File roundTrippedConllFile2 = folder.newFile("roundtrip2.conll");
		new CoNLLCoref2012DocumentWriter(IncludeCorefType.IDENT).serialize(td, roundTrippedConllFile2, encoding);

		expectedLines = FileReaderUtil.loadLinesFromFile(originalConllFile, encoding);
		observedLines = FileReaderUtil.loadLinesFromFile(roundTrippedConllFile2, encoding);

		assertTrue(FileComparisonUtil.hasExpectedLines(observedLines, expectedLines, null, LineOrder.AS_IN_FILE,
				ColumnOrder.AS_IN_FILE, LineTrim.OFF, ShowWhiteSpace.OFF));

	}

}
