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

import edu.ucdenver.ccp.datasource.fileparsers.SingleLineFileRecord;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * This data structure is designed to hold records from files using the CoNLL-U
 * format as described here: https://universaldependencies.org/format.html This
 * code was written specifically for extracting dependency parse information so
 * it may be incomplete in regards to some of the other columns.
 * 
 * <pre>
 * Sentences consist of one or more word lines, and word lines contain the following fields:
 * 
 * ID: Word index, integer starting at 1 for each new sentence; may be a range for multiword tokens; may be a decimal number for empty nodes (decimal numbers can be lower than 1 but must be greater than 0).
 * FORM: Word form or punctuation symbol.
 * LEMMA: Lemma or stem of word form.
 * UPOS: Universal part-of-speech tag.
 * XPOS: Language-specific part-of-speech tag; underscore if not available.
 * FEATS: List of morphological features from the universal feature inventory or from a defined language-specific extension; underscore if not available.
 * HEAD: Head of the current word, which is either a value of ID or zero (0).
 * DEPREL: Universal dependency relation to the HEAD (root iff HEAD = 0) or a defined language-specific subtype of one.
 * DEPS: Enhanced dependency graph in the form of a list of head-deprel pairs.
 * MISC: Any other annotation.
 * The fields DEPS and MISC replace the obsolete fields PHEAD and PDEPREL of the CoNLL-X format. In addition, we have modified the usage of the ID, FORM, LEMMA, XPOS, FEATS and HEAD fields as explained below.
 * 
 * The fields must additionally meet the following constraints:
 * 
 * Fields must not be empty.
 * Fields other than FORM and LEMMA must not contain space characters.
 * Underscore (_) is used to denote unspecified values in all fields except ID. Note that no format-level distinction is made for the rare cases where the FORM or LEMMA is the literal underscore – processing in such cases is application-dependent. Further, in UD treebanks the UPOS, HEAD, and DEPREL columns are not allowed to be left unspecified.
 * </pre>
 *
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class CoNLLUFileRecord extends SingleLineFileRecord {

	private final int wordIndex;
	private final String form;
	private final String lemma;
	private final String universalPartOfSpeechTag;
	private final String languageSpecificPartOfSpeechTag;
	private final String morphologicFeatures;
	private Integer head;
	private String dependencyRelation;
	private final String enhancedDependencyGraph;
	private String miscellaneous;

	public CoNLLUFileRecord(int wordIndex, String form, String lemma, String universalPartOfSpeechTag,
			String languageSpecificPartOfSpeechTag, String morphologicFeatures, Integer head, String dependencyRelation,
			String enhancedDependencyGraph, String miscellaneous, long byteOffset, long lineNumber) {
		super(byteOffset, lineNumber);
		this.wordIndex = wordIndex;
		this.form = form;
		this.lemma = lemma;
		this.universalPartOfSpeechTag = universalPartOfSpeechTag;
		this.languageSpecificPartOfSpeechTag = languageSpecificPartOfSpeechTag;
		this.morphologicFeatures = morphologicFeatures;
		this.head = head;
		this.dependencyRelation = dependencyRelation;
		this.enhancedDependencyGraph = enhancedDependencyGraph;
		this.miscellaneous = miscellaneous;
	}

	public String toCoNLLUFormatString() {
		StringBuffer sb = new StringBuffer();

		sb.append(this.wordIndex + "\t");
		sb.append(this.form + "\t");
		sb.append(underscoreIfNull(this.lemma) + "\t");
		sb.append(this.universalPartOfSpeechTag + "\t");
		sb.append(underscoreIfNull(this.languageSpecificPartOfSpeechTag) + "\t");
		sb.append(underscoreIfNull(this.morphologicFeatures) + "\t");
		sb.append(underscoreIfNull(this.head) + "\t");
		sb.append(underscoreIfNull(this.dependencyRelation) + "\t");
		sb.append(underscoreIfNull(this.enhancedDependencyGraph) + "\t");
		/*
		 * miscellaneous gets used to store span and other information by some
		 * of the other file converters. so we just print out underscore.
		 */
		// sb.append(underscoreIfNull(this.miscellaneous));
		sb.append("_");

		return sb.toString();
	}

	private String underscoreIfNull(Object o) {
		return (o == null) ? "_" : o.toString();
	}

}
