package edu.ucdenver.ccp.file.conversion.util;

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

import java.util.List;

import org.junit.Test;

import edu.ucdenver.ccp.common.collections.CollectionsUtil;
import edu.ucdenver.ccp.nlp.core.annotation.Span;

public class DocumentReaderUtilTest {

	@Test
	public void testAdjacentSpanConsolidation() {
		// 012345678901234567890123456789012345678901234567890123456789
		String documentText = "The quick brown fox jumped over the lazy dog.";

		Span quickSpan = new Span(4, 9);
		Span brownSpan = new Span(10, 15);
		Span foxSpan = new Span(16, 19);
		Span quickBrownFoxSpan = new Span(4, 19);

		Span lazySpan = new Span(36, 40);
		Span dogSpan = new Span(41, 44);
		List<Span> inputSpans = CollectionsUtil.createList(quickSpan, brownSpan, foxSpan);
		List<Span> expectedUpdatedSpans = CollectionsUtil.createList(quickBrownFoxSpan);
		List<Span> updatedSpans = DocumentReaderUtil.consolidateAdjacentSpans(inputSpans, documentText, "12345");
		assertEquals(expectedUpdatedSpans, updatedSpans);

		inputSpans = CollectionsUtil.createList(quickSpan, foxSpan);
		expectedUpdatedSpans = CollectionsUtil.createList(quickSpan, foxSpan);
		updatedSpans = DocumentReaderUtil.consolidateAdjacentSpans(inputSpans, documentText, "12345");
		assertEquals(expectedUpdatedSpans, updatedSpans);

		inputSpans = CollectionsUtil.createList(quickBrownFoxSpan, foxSpan);
		expectedUpdatedSpans = CollectionsUtil.createList(quickBrownFoxSpan);
		updatedSpans = DocumentReaderUtil.consolidateAdjacentSpans(inputSpans, documentText, "12345");
		assertEquals(expectedUpdatedSpans, updatedSpans);
		
		inputSpans = CollectionsUtil.createList(quickBrownFoxSpan, brownSpan);
		expectedUpdatedSpans = CollectionsUtil.createList(quickBrownFoxSpan);
		updatedSpans = DocumentReaderUtil.consolidateAdjacentSpans(inputSpans, documentText, "12345");
		assertEquals(expectedUpdatedSpans, updatedSpans);

	}

}
