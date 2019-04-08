package edu.ucdenver.ccp.file.conversion.treebank;

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import edu.ucdenver.ccp.common.collections.CollectionsUtil;
import edu.ucdenver.ccp.common.file.CharacterEncoding;
import edu.ucdenver.ccp.common.file.FileWriterUtil;
import edu.ucdenver.ccp.file.conversion.TextDocument;
import edu.ucdenver.ccp.nlp.core.annotation.TextAnnotation;

public class TreebankDocumentReaderTest {

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Test
	public void testTreebankDocumentReader() throws IOException {
		CharacterEncoding encoding = CharacterEncoding.UTF_8;
		File treebankDir = folder.newFolder("penn");

		File treebankFile = new File(treebankDir, "12345.tree");
		List<String> treebankLines = CollectionsUtil.createList(
				"( (TITLE (FRAG (NP (NP (JJ Intraocular) (NN pressure)) (PP (IN in) (NP (ADJP (RB genetically) (JJ distinct)) (NNS mice)))) (: :) (NP (DT an) (NML (NML (NN update)) (CC and) (NML (NN strain) (NN survey)))))) )",
				"( (HEADING (NP (NN Abstract))) )", "( (HEADING (NP (NN Background))) )",
				"( (S (NP-SBJ-1 (JJ Little)) (VP (VBZ is) (VP (VBN known) (NP-1 (-NONE- *)) (PP (IN about) (NP (NP (JJ genetic) (NNS factors)) (VP (VBG affecting) (NP (NP (NP (JJ intraocular) (NN pressure)) (NP (-LRB- -LRB-) (NN IOP))) (-RRB- -RRB-) (PP-LOC (IN in) (NP (NP (NNS mice)) (CC and) (NP (JJ other) (NNS mammals)))))))))) (. .)) )");
		FileWriterUtil.printLines(treebankLines, treebankFile, encoding);

		File documentTextFile = folder.newFile("12345.txt");
		List<String> txtLines = CollectionsUtil.createList(
				"Intraocular pressure in genetically distinct mice: an update and strain survey", "", "Abstract", "",
				"Background", "",
				"Little is known about genetic factors affecting intraocular pressure (IOP) in mice and other mammals.");
		FileWriterUtil.printLines(txtLines, documentTextFile, encoding);

		TextDocument td = new TreebankDocumentReader().readDocument("12345", "PMC", treebankFile, documentTextFile,
				encoding);

		assertNotNull(td);
		List<TextAnnotation> annotations = td.getAnnotations();
		assertEquals(65, annotations.size());

	}

	@Test
	public void testTreebankDocumentReader_POS() throws IOException {
		CharacterEncoding encoding = CharacterEncoding.UTF_8;
		File treebankDir = folder.newFolder("penn");

		File treebankFile = new File(treebankDir, "12345.tree");
		List<String> treebankLines = CollectionsUtil.createList(
				"( (TITLE (FRAG (NP (NP (JJ Intraocular) (NN pressure)) (PP (IN in) (NP (ADJP (RB genetically) (JJ distinct)) (NNS mice)))) (: :) (NP (DT an) (NML (NML (NN update)) (CC and) (NML (NN strain) (NN survey)))))) )",
				"( (HEADING (NP (NN Abstract))) )", "( (HEADING (NP (NN Background))) )",
				"( (S (NP-SBJ-1 (JJ Little)) (VP (VBZ is) (VP (VBN known) (NP-1 (-NONE- *)) (PP (IN about) (NP (NP (JJ genetic) (NNS factors)) (VP (VBG affecting) (NP (NP (NP (JJ intraocular) (NN pressure)) (NP (-LRB- -LRB-) (NN IOP))) (-RRB- -RRB-) (PP-LOC (IN in) (NP (NP (NNS mice)) (CC and) (NP (JJ other) (NNS mammals)))))))))) (. .)) )");
		FileWriterUtil.printLines(treebankLines, treebankFile, encoding);

		File documentTextFile = folder.newFile("12345.txt");
		List<String> txtLines = CollectionsUtil.createList(
				"Intraocular pressure in genetically distinct mice: an update and strain survey", "", "Abstract", "",
				"Background", "",
				"Little is known about genetic factors affecting intraocular pressure (IOP) in mice and other mammals.");
		FileWriterUtil.printLines(txtLines, documentTextFile, encoding);

		TextDocument td = new SentenceTokenOnlyTreebankDocumentReader().readDocument("12345", "PMC", treebankFile,
				documentTextFile, encoding);

		assertNotNull(td);
		List<TextAnnotation> annotations = td.getAnnotations();
		assertEquals("there should be 32 tokens with part-of-speech tags and 4 sentences", 36, annotations.size());

	}

	@Test
	public void testTreebankDocumentReader_Sentence() throws IOException {
		CharacterEncoding encoding = CharacterEncoding.UTF_8;
		File treebankDir = folder.newFolder("penn");

		File treebankFile = new File(treebankDir, "12345.tree");
		List<String> treebankLines = CollectionsUtil.createList(
				"( (TITLE (FRAG (NP (NP (JJ Intraocular) (NN pressure)) (PP (IN in) (NP (ADJP (RB genetically) (JJ distinct)) (NNS mice)))) (: :) (NP (DT an) (NML (NML (NN update)) (CC and) (NML (NN strain) (NN survey)))))) )",
				"( (HEADING (NP (NN Abstract))) )", "( (HEADING (NP (NN Background))) )",
				"( (S (NP-SBJ-1 (JJ Little)) (VP (VBZ is) (VP (VBN known) (NP-1 (-NONE- *)) (PP (IN about) (NP (NP (JJ genetic) (NNS factors)) (VP (VBG affecting) (NP (NP (NP (JJ intraocular) (NN pressure)) (NP (-LRB- -LRB-) (NN IOP))) (-RRB- -RRB-) (PP-LOC (IN in) (NP (NP (NNS mice)) (CC and) (NP (JJ other) (NNS mammals)))))))))) (. .)) )");
		FileWriterUtil.printLines(treebankLines, treebankFile, encoding);

		File documentTextFile = folder.newFile("12345.txt");
		List<String> txtLines = CollectionsUtil.createList(
				"Intraocular pressure in genetically distinct mice: an update and strain survey", "", "Abstract", "",
				"Background", "",
				"Little is known about genetic factors affecting intraocular pressure (IOP) in mice and other mammals.");
		FileWriterUtil.printLines(txtLines, documentTextFile, encoding);

		TextDocument td = new SentenceTokenOnlyTreebankDocumentReader().readDocument("12345", "PMC", treebankFile,
				documentTextFile, encoding);

		assertNotNull(td);
		List<TextAnnotation> annotations = td.getAnnotations();
		assertEquals("there should be 4 'sentence' annotations and 32 token annotations", 36, annotations.size());

	}

}
