package edu.ucdenver.ccp.file.conversion.knowtator;

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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import edu.ucdenver.ccp.common.collections.CollectionsUtil;
import edu.ucdenver.ccp.common.file.CharacterEncoding;
import edu.ucdenver.ccp.file.conversion.TextDocument;
import edu.ucdenver.ccp.nlp.core.annotation.TextAnnotation;

public class KnowtatorDocumentReaderTest {

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	@Test
	public void testDocumentReader() throws IOException {
		CharacterEncoding encoding = CharacterEncoding.UTF_8;

		/* @formatter:on */
		List<String> annotLines = CollectionsUtil.createList("<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
				"<annotations textSource=\"11532192.txt\">", "<annotation>",
				"  <mention id=\"11532192SHM_Instance_70268\" />",
				"  <annotator id=\"11532192SHM_Instance_280000\">CCP Colorado Computational Pharmacology, UC Denver</annotator>",
				"  <span start=\"39854\" end=\"39872\" />", "  <spannedText>carbonic anhydrase</spannedText>",
				"  <creationDate>Sat Mar 28 00:34:55 MDT 2009</creationDate>", "</annotation>", "<annotation>",
				"  <mention id=\"11532192SHM_Instance_70288\" />",
				"  <annotator id=\"11532192SHM_Instance_280000\">CCP Colorado Computational Pharmacology, UC Denver</annotator>",
				"  <span start=\"39854\" end=\"39872\" />", "  <spannedText>carbonic anhydrase</spannedText>",
				"  <creationDate>Sat Mar 28 00:36:45 MDT 2009</creationDate>", "</annotation>", "<annotation>",
				"  <mention id=\"11532192SHM_Instance_70270\" />",
				"  <annotator id=\"11532192SHM_Instance_280000\">CCP Colorado Computational Pharmacology, UC Denver</annotator>",
				"  <span start=\"39874\" end=\"39876\" />", "  <spannedText>CA</spannedText>",
				"  <creationDate>Sat Mar 28 00:34:57 MDT 2009</creationDate>", "</annotation>", "<annotation>",
				"  <mention id=\"11532192SHM_Instance_100068\" />",
				"  <annotator id=\"11532192SHM_Instance_280000\">CCP Colorado Computational Pharmacology, UC Denver</annotator>",
				"  <span start=\"31746\" end=\"31764\" />", "  <spannedText>carbonic anhydrase</spannedText>",
				"  <creationDate>Mon Mar 30 20:21:54 MDT 2009</creationDate>", "</annotation>", "<annotation>",
				"  <mention id=\"11532192SHM_Instance_100071\" />",
				"  <annotator id=\"11532192SHM_Instance_280000\">CCP Colorado Computational Pharmacology, UC Denver</annotator>",
				"  <span start=\"31826\" end=\"31828\" />", "  <spannedText>CA</spannedText>",
				"  <creationDate>Mon Mar 30 20:21:59 MDT 2009</creationDate>", "</annotation>",
				"<classMention id=\"11532192SHM_Instance_100068\">",
				"  <mentionClass id=\"IDENTITY chain\">IDENTITY chain</mentionClass>",
				"  <hasSlotMention id=\"11532192SHM_Instance_100073\" />", "</classMention>",
				"<complexSlotMention id=\"11532192SHM_Instance_100073\">",
				"  <mentionSlot id=\"Coreferring strings\" />",
				"  <complexSlotMentionValue value=\"11532192SHM_Instance_100071\" />",
				"  <complexSlotMentionValue value=\"11532192SHM_Instance_70288\" />", "</complexSlotMention>",
				"<classMention id=\"11532192SHM_Instance_70288\">",
				"  <mentionClass id=\"APPOS relation\">APPOS relation</mentionClass>",
				"  <hasSlotMention id=\"11532192SHM_Instance_70290\" />",
				"  <hasSlotMention id=\"11532192SHM_Instance_70291\" />", "</classMention>",
				"<complexSlotMention id=\"11532192SHM_Instance_70290\">", "  <mentionSlot id=\"APPOS Attributes\" />",
				"  <complexSlotMentionValue value=\"11532192SHM_Instance_70270\" />", "</complexSlotMention>",
				"<classMention id=\"11532192SHM_Instance_70270\">",
				"  <mentionClass id=\"Noun Phrase\">Noun Phrase</mentionClass>", "</classMention>",
				"<complexSlotMention id=\"11532192SHM_Instance_70291\">", "  <mentionSlot id=\"APPOS Head\" />",
				"  <complexSlotMentionValue value=\"11532192SHM_Instance_70268\" />", "</complexSlotMention>",
				"<classMention id=\"11532192SHM_Instance_70268\">",
				"  <mentionClass id=\"Noun Phrase\">Noun Phrase</mentionClass>", "</classMention>",
				"<classMention id=\"11532192SHM_Instance_100071\">",
				"  <mentionClass id=\"Noun Phrase\">Noun Phrase</mentionClass>", "</classMention>", "</annotations>");
		/* @formatter:off */
		
		StringBuffer annotBuffer = new StringBuffer();
		for (String line : annotLines) {
			annotBuffer.append(line + "\n");
		}
		
		TextDocument td = new KnowtatorDocumentReader().readDocument("12345", "PMC", new ByteArrayInputStream(annotBuffer.toString().getBytes()), new ByteArrayInputStream("doc text".getBytes()), encoding);
		
		List<TextAnnotation> annotations = td.getAnnotations();
		assertEquals("there should be 5 annotations", 5, annotations.size());
		for (TextAnnotation annot: annotations) {
			assertNotNull("each annot should have a class mention", annot.getClassMention());
			System.out.println(annot.toString());
		}
		
	}
	
	
	@Test
	public void test() throws IOException {
		CharacterEncoding encoding = CharacterEncoding.UTF_8;
		File inputFile = new File("/Users/bill/Dropbox/work/projects/craft-shared-task-2019/knowtator-xsd-gen/sample-xml/11532192.txt.knowtator.xml");
		File documentTextFile = new File("/Users/bill/Dropbox/work/projects/craft-shared-task-2019/CRAFT.bill.git/articles/txt/11532192.txt");
		
		TextDocument td = new KnowtatorDocumentReader().readDocument("12345", "PMC", inputFile, documentTextFile, encoding);
		
		
	}

}
