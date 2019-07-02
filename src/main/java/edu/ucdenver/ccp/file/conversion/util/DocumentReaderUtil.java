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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import edu.ucdenver.ccp.nlp.core.annotation.Span;
import edu.ucdenver.ccp.nlp.core.annotation.TextAnnotation;
import edu.ucdenver.ccp.nlp.core.annotation.TextAnnotationUtil;
import edu.ucdenver.ccp.nlp.core.annotation.impl.DefaultTextAnnotation;

public class DocumentReaderUtil {

	/**
	 * cycle through each annotation and check to make sure discontinuous spans are indeed
	 * discontinuous. If an annotation has multiple adjacent spans, then splice them together into a
	 * single continuous span. We define "adjacent" to mean spans that are separated by only
	 * whitespace.
	 * 
	 * @param annotations
	 * @param sourceId
	 * @return list of annotations where invalid spans were detected
	 */
	public static List<TextAnnotation> validateSpans(Collection<TextAnnotation> annotations, String documentText,
			String sourceId) {
		List<TextAnnotation> annotsWithInvalidSpans = new ArrayList<TextAnnotation>();
		for (TextAnnotation ta : annotations) {
			if (ta.getSpans().size() > 1) {
				List<Span> updatedSpans = consolidateAdjacentSpans(ta.getSpans(), documentText, sourceId);
				if (!updatedSpans.equals(ta.getSpans())) {
					TextAnnotation invalidTa = new DefaultTextAnnotation(0, 1);
					TextAnnotationUtil.swapAnnotationInfo(ta, invalidTa);
					annotsWithInvalidSpans.add(invalidTa);
					ta.setSpans(updatedSpans);
				}
			}
		}
		return annotsWithInvalidSpans;
	}

	public static List<Span> consolidateAdjacentSpans(List<Span> spans, String documentText, String sourceId) {
		List<Span> updatedSpans = new ArrayList<Span>();
		if (spans.size() > 1) {

			Collections.sort(spans, Span.ASCENDING());
			Span currentSpan = new Span(spans.get(0).getSpanStart(), spans.get(0).getSpanEnd());
			for (int i = 1; i < spans.size(); i++) {
				Span nextSpan = spans.get(i);
				if (nextSpan.overlaps(currentSpan)) {
					// if the spans overlap, then take the maximal span
					currentSpan.setSpanEnd(Math.max(currentSpan.getSpanEnd(), nextSpan.getSpanEnd()));
					System.err.println("WARNING: Consolidating overlapping spans (" + sourceId + ") : "
							+ currentSpan.toString() + " + " + nextSpan.toString());
				} else {
					String interveningText = documentText.substring(currentSpan.getSpanEnd(), nextSpan.getSpanStart());
					if (interveningText.trim().isEmpty()) {
						// then we need to splice these spans together
						System.err.println("WARNING: Splicing adjacent discontinuous spans (" + sourceId + ") : "
								+ currentSpan.toString() + " + " + nextSpan.toString());
						currentSpan.setSpanEnd(nextSpan.getSpanEnd());
					} else {
						// no splice between the current and next spans, so add the current span to
						// the
						// updated list and reset the current span as the next span.
						updatedSpans.add(currentSpan);
						currentSpan = nextSpan;
					}
				}
			}
			updatedSpans.add(currentSpan);
		}
		return updatedSpans;
	}
}
