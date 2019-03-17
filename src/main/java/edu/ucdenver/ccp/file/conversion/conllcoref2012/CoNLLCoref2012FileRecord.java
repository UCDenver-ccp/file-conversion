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

import java.util.List;

import edu.ucdenver.ccp.datasource.fileparsers.SingleLineFileRecord;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * This data structure is designed to hold records from files using the CoNLL
 * Coreference 2011/12 format as described here:
 * http://conll.cemantix.org/2012/data.html. This code was written specifically
 * for extracting coreference information so it may be incomplete in regards to
 * non-coreference-related data.
 * 
 * <pre>
 * Column	Type	Description
 * 1	Document ID	This is a variation on the document filename
 * 2	Part number	Some files are divided into multiple parts numbered as 000, 001, 002, ... etc.
 * 3	Word number	
 * 4	Word itself	This is the token as segmented/tokenized in the Treebank. Initially the *_skel file contain the placeholder [WORD] which gets replaced by the actual token from the Treebank which is part of the OntoNotes release.
 * 5	Part-of-Speech	
 * 6	Parse bit	This is the bracketed structure broken before the first open parenthesis in the parse, and the word/part-of-speech leaf replaced with a *. The full parse can be created by substituting the asterix with the "([pos] [word])" string (or leaf) and concatenating the items in the rows of that column.
 * 7	Predicate lemma	The predicate lemma is mentioned for the rows for which we have semantic role information. All other rows are marked with a "-"
 * 8	Predicate Frameset ID	This is the PropBank frameset ID of the predicate in Column 7.
 * 9	Word sense	This is the word sense of the word in Column 3.
 * 10	Speaker/Author	This is the speaker or author name where available. Mostly in Broadcast Conversation and Web Log data.
 * 11	Named Entities	These columns identifies the spans representing various named entities.
 * 12:N	Predicate Arguments	There is one column each of predicate argument structure information for the predicate mentioned in Column 7.
 * N	Coreference
 * </pre>
 *
 */
@Data
@EqualsAndHashCode(callSuper = false)
public class CoNLLCoref2012FileRecord extends SingleLineFileRecord {

	private final String documentId;
	private final String partNumber;
	private final int workNumber;
	private final String word;
	private final String partOfSpeech;
	private final String parseBit;
	private final String predicateLemma;
	private final String predicateFramesetId;
	private final String wordSense;
	private final String speakerAuthor;
	private final String namedEntities;
	private final List<String> predicateArguments;
	private final String coreference;

	public CoNLLCoref2012FileRecord(String documentId, String partNumber, int workNumber, String word,
			String partOfSpeech, String parseBit, String predicateLemma, String predicateFramesetId, String wordSense,
			String speakerAuthor, String namedEntities, List<String> predicateArguments, String coreference,
			long byteOffset, long lineNumber) {
		super(byteOffset, lineNumber);
		this.documentId = documentId;
		this.partNumber = partNumber;
		this.workNumber = workNumber;
		this.word = word;
		this.partOfSpeech = partOfSpeech;
		this.parseBit = parseBit;
		this.predicateLemma = predicateLemma;
		this.predicateFramesetId = predicateFramesetId;
		this.wordSense = wordSense;
		this.speakerAuthor = speakerAuthor;
		this.namedEntities = namedEntities;
		this.predicateArguments = predicateArguments;
		this.coreference = coreference;
	}

}
