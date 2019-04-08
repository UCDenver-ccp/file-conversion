package edu.ucdenver.ccp.file.conversion.brat;

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
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;

import edu.ucdenver.ccp.common.collections.CollectionsUtil;
import edu.ucdenver.ccp.common.file.CharacterEncoding;
import edu.ucdenver.ccp.common.file.FileReaderUtil;
import edu.ucdenver.ccp.common.file.FileWriterUtil;
import edu.ucdenver.ccp.common.io.StreamUtil;
import edu.ucdenver.ccp.file.conversion.TextDocument;
import edu.ucdenver.ccp.nlp.core.annotation.Span;
import edu.ucdenver.ccp.nlp.core.annotation.TextAnnotation;
import edu.ucdenver.ccp.nlp.core.annotation.TextAnnotationFactory;
import edu.ucdenver.ccp.nlp.core.mention.ComplexSlotMention;
import edu.ucdenver.ccp.nlp.core.mention.impl.DefaultClassMention;
import edu.ucdenver.ccp.nlp.core.mention.impl.DefaultComplexSlotMention;

public class BratConfigFileWriterTest {
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Test
	public void testConfigFileCreation() throws IOException, OWLOntologyCreationException {

		File annotationDirectory = folder.newFolder("annotfiles");
		File ontologyDirectory = folder.newFolder("ontfiles");
		File annotFile = new File(annotationDirectory, "12345.ann");
		CharacterEncoding encoding = CharacterEncoding.UTF_8;

		TextAnnotationFactory factory = TextAnnotationFactory.createFactoryWithDefaults();
		List<TextAnnotation> annotations = new ArrayList<TextAnnotation>();
		annotations.add(factory.createAnnotation(0, 5, "BRCA2", new DefaultClassMention("CHEBI:12345")));
		TextAnnotation ppardAnnot1 = factory.createAnnotation(55, 60, "PPARD", new DefaultClassMention("PR:12345"));
		annotations.add(ppardAnnot1);

		TextAnnotation ppardAnnot2 = factory.createAnnotation(100, 104, "PPAR gamma",
				new DefaultClassMention("PR:12345"));
		ppardAnnot2.addSpan(new Span(115, 120));
		annotations.add(ppardAnnot2);

		/* link the PPARD annotations with a relation (type = identity chain) */
		String relationType = "related to";
		ComplexSlotMention csm = new DefaultComplexSlotMention(relationType);
		csm.addClassMention(ppardAnnot2.getClassMention());
		ppardAnnot1.getClassMention().addComplexSlotMention(csm);

		TextDocument td = new TextDocument("12345", "PMC",
				"this is where the document text goes this is where the document"
						+ " text goes this is where the document text goes this is "
						+ "where the document text goes this is where the document text"
						+ " goes this is where the document text goes");
		td.setAnnotations(annotations);

		assertEquals("should be 3 annotations", 3, td.getAnnotations().size());

		new BratDocumentWriter().serialize(td, annotFile, encoding);

		String expectedSerializedAnnotations = ("T1\tCHEBI:12345 0 5\tBRCA2\n" + "T2\tPR:12345 55 60\tPPARD\n"
				+ "T3\tPR:12345 100 104;115 120\tPPAR gamma\n" + "R1\t" + "related^to" + " Arg1:T2 Arg2:T3\n");

		String observedAnnotations = StreamUtil
				.toString(new InputStreamReader(new FileInputStream(annotFile), encoding.getDecoder()));

		assertEquals("annotations in serialized BioNLP format not as expected", expectedSerializedAnnotations,
				observedAnnotations);

		Map<String, String> conceptTypeToColorMap = new HashMap<String, String>();
		conceptTypeToColorMap.put("PR", "bgcolor:#123456");
		conceptTypeToColorMap.put("CHEBI", "bgcolor:#f0f0f0");

		File ontFile1 = new File(ontologyDirectory, "ont1.obo");
		File ontFile2 = new File(ontologyDirectory, "ont2.obo");
		List<File> ontologyFiles = CollectionsUtil.createList(ontFile1, ontFile2);

		FileWriterUtil.printLines(
				CollectionsUtil.createList("format-version: 1.2", "", "[Term]", "id: CHEBI:12345", "name: protein"),
				ontFile1, encoding);
		FileWriterUtil.printLines(
				CollectionsUtil.createList("format-version: 1.2", "", "[Term]", "id: PR:12345", "name: gene"), ontFile2,
				encoding);

		BratConfigFileWriter.createConfFiles(annotationDirectory, ontologyFiles, encoding, conceptTypeToColorMap);

		File docTextFile = new File(annotationDirectory, "12345.txt");
		assertTrue(docTextFile.exists());

		File annotationConfFile = new File(annotationDirectory, "annotation.conf");
		File visualConfFile = new File(annotationDirectory, "visual.conf");

		assertTrue(annotationConfFile.exists());

		List<String> observedLines = new ArrayList<String>(
				FileReaderUtil.loadLinesFromFile(annotationConfFile, encoding));
		List<String> expectedLines = CollectionsUtil.createList("[entities]", "CHEBI:12345", "PR:12345", "[attributes]",
				"[relations]", "related^to", "[events]", "");

		assertEquals("lines are not as expected in annotation.conf", expectedLines, observedLines);

		assertTrue(visualConfFile.exists());

		observedLines = new ArrayList<String>(FileReaderUtil.loadLinesFromFile(visualConfFile, encoding));
		expectedLines = CollectionsUtil.createList("[drawing]", "CHEBI:12345\tbgcolor:#f0f0f0",
				"PR:12345\tbgcolor:#123456", "[labels]", "CHEBI:12345 | protein", "PR:12345 | gene", "");

		assertEquals("lines are not as expected in visual.conf", expectedLines, observedLines);

	}

}
